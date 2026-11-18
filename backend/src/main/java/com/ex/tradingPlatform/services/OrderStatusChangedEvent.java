package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.entities.OrderStatus;

import java.math.BigDecimal;

/** Published when matching fills or rejects a pending order; {@code filledPrice} is null unless FILLED. */
public record OrderStatusChangedEvent(Long userId, Long orderId, OrderStatus status, BigDecimal filledPrice) {
}
