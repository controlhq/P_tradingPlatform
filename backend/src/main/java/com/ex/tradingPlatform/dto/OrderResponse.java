package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderStatus;
import com.ex.tradingPlatform.entities.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long instrumentId,
        String symbol,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        int quantity,
        BigDecimal limitPrice,
        BigDecimal stopPrice,
        BigDecimal filledPrice,
        Instant filledAt,
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getInstrument().getId(),
                order.getInstrument().getSymbol(),
                order.getSide(),
                order.getType(),
                order.getStatus(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getStopPrice(),
                order.getFilledPrice(),
                order.getFilledAt(),
                order.getCreatedAt());
    }
}
