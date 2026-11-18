package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.InstrumentResponse;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.PriceTick;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    static final String INSTRUMENT_NOT_FOUND = "INSTRUMENT_NOT_FOUND";

    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;

    @Transactional(readOnly = true)
    public List<InstrumentResponse> listActive() {
        List<Instrument> instruments = instrumentRepository.findAllByActiveTrueOrderBySymbolAsc();
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

    private Instrument findOrThrow(Long id) {
        return instrumentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND, "Instrument not found."));
    }

    private InstrumentResponse toResponse(Instrument instrument) {
        PriceTick latest = priceTickRepository
                .findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId())
                .orElse(null);
        return InstrumentResponse.from(instrument, latest);
    }
}
