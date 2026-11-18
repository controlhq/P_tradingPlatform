package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.OrderStatus;

import java.math.BigDecimal;

/** Payload of the SSE "order" event, sent only to the owner of the order. */
public record OrderStreamEvent(Long orderId, OrderStatus status, BigDecimal filledPrice) {
}
