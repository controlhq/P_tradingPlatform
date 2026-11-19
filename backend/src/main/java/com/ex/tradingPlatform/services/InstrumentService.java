package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.CreateInstrumentRequest;
import com.ex.tradingPlatform.dto.InstrumentResponse;
import com.ex.tradingPlatform.dto.PricePointResponse;
import com.ex.tradingPlatform.dto.UpdateInstrumentRequest;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.PriceTick;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    static final String INSTRUMENT_NOT_FOUND = "INSTRUMENT_NOT_FOUND";
    static final String SYMBOL_TAKEN = "SYMBOL_TAKEN";
    static final String INVALID_TIME_RANGE = "INVALID_TIME_RANGE";
    private static final String SYMBOL_UNIQUE_CONSTRAINT = "uq_instruments_symbol";
    private static final int PRICE_SCALE = 4;

    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<InstrumentResponse> listActive() {
        return withLatestPrices(instrumentRepository.findAllByActiveTrueOrderBySymbolAsc());
    }

    /** Active and inactive instruments, for the admin panel (BR56). */
    @Transactional(readOnly = true)
    public List<InstrumentResponse> listAll() {
        return withLatestPrices(instrumentRepository.findAllByOrderBySymbolAsc());
    }

    private List<InstrumentResponse> withLatestPrices(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }

        Map<Long, PriceTick> latestByInstrument = priceTickRepository
                .findLatestByInstrumentIds(instruments.stream().map(Instrument::getId).toList())
                .stream()
                .collect(Collectors.toMap(t -> t.getInstrument().getId(), Function.identity()));

        return instruments.stream()
                .map(i -> InstrumentResponse.from(i, latestByInstrument.get(i.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public InstrumentResponse getById(Long id) {
        Instrument instrument = findOrThrow(id);
        return toResponse(instrument);
    }

    /**
     * The {@code limit} most recent ticks with {@code from <= ts <= to}, oldest first.
     * A missing {@code from} means no lower bound, a missing {@code to} means now.
     */
    @Transactional(readOnly = true)
    public List<PricePointResponse> priceHistory(Long id, Instant from, Instant to, int limit) {
        if (!instrumentRepository.existsById(id)) {
            throw instrumentNotFound();
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_TIME_RANGE, "'from' must not be after 'to'.");
        }

        List<PriceTick> newestFirst = priceTickRepository.findByInstrumentIdAndTsBetweenOrderByTsDescIdDesc(
                id, from != null ? from : Instant.EPOCH, to != null ? to : clock.instant(), Limit.of(limit));
        return newestFirst.reversed().stream().map(PricePointResponse::from).toList();
    }

    @Transactional
    public InstrumentResponse create(CreateInstrumentRequest request) {
        if (instrumentRepository.existsBySymbol(request.symbol())) {
            throw symbolTaken();
        }

        Instant now = clock.instant();
        Instrument instrument = new Instrument(request.symbol(), request.name(), now);
        try {
            // flush now so a concurrent duplicate hits uq_instruments_symbol here, not at commit
            instrumentRepository.saveAndFlush(instrument);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException cve
                    && SYMBOL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
                throw symbolTaken();
            }
            throw ex;
        }

        BigDecimal price = request.initialPrice().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        PriceTick tick = priceTickRepository.save(new PriceTick(instrument, price, now));
        return InstrumentResponse.from(instrument, tick);
    }

    @Transactional
    public InstrumentResponse update(Long id, UpdateInstrumentRequest request) {
        Instrument instrument = findOrThrow(id);
        instrument.setActive(request.active());
        return toResponse(instrument);
    }

    private static BusinessException symbolTaken() {
        return new BusinessException(HttpStatus.CONFLICT, SYMBOL_TAKEN, "Symbol is already taken.");
    }

    private static BusinessException instrumentNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND, "Instrument not found.");
    }

    private Instrument findOrThrow(Long id) {
        return instrumentRepository.findById(id).orElseThrow(InstrumentService::instrumentNotFound);
    }

    private InstrumentResponse toResponse(Instrument instrument) {
        PriceTick latest = priceTickRepository
                .findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId())
                .orElse(null);
        return InstrumentResponse.from(instrument, latest);
    }
}
