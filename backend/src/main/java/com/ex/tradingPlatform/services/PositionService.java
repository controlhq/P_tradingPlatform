package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.PositionResponse;
import com.ex.tradingPlatform.entities.Position;
import com.ex.tradingPlatform.entities.PriceTick;
import com.ex.tradingPlatform.repositories.PositionRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionService {

    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PositionRepository positionRepository;
    private final PriceTickRepository priceTickRepository;

    /** The user's positions ordered by symbol, valued at the latest price of each instrument. */
    @Transactional(readOnly = true)
    public List<PositionResponse> listForUser(Long userId) {
        List<Position> positions = positionRepository.findByUserIdOrderByInstrumentSymbolAsc(userId);
        if (positions.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> latestPrices = priceTickRepository
                .findLatestByInstrumentIds(positions.stream().map(p -> p.getInstrument().getId()).toList())
                .stream()
                .collect(Collectors.toMap(t -> t.getInstrument().getId(), PriceTick::getPrice));

        return positions.stream()
                .map(p -> toResponse(p, latestPrices.get(p.getInstrument().getId())))
                .toList();
    }

    private static PositionResponse toResponse(Position position, BigDecimal currentPrice) {
        BigDecimal marketValue = null;
        BigDecimal pnl = null;
        BigDecimal pnlPct = null;
        if (currentPrice != null) {
            BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());
            BigDecimal cost = position.getAvgPrice().multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            marketValue = currentPrice.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            pnl = marketValue.subtract(cost);
            pnlPct = cost.signum() == 0
                    ? BigDecimal.ZERO.setScale(PERCENT_SCALE)
                    : pnl.multiply(HUNDRED).divide(cost, PERCENT_SCALE, RoundingMode.HALF_UP);
        }
        return new PositionResponse(
                position.getInstrument().getId(),
                position.getInstrument().getSymbol(),
                position.getQuantity(),
                position.getBlockedQuantity(),
                position.getAvgPrice(),
                currentPrice,
                marketValue,
                pnl,
                pnlPct);
    }
}
