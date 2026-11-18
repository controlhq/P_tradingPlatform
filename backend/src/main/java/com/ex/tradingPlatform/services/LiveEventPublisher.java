package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.OrderStreamEvent;
import com.ex.tradingPlatform.dto.PriceStreamEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards committed domain events to the SSE clients, so clients never see a rolled-back change. */
@Service
@RequiredArgsConstructor
public class LiveEventPublisher {

    static final String PRICE_EVENT = "price";
    static final String ORDER_EVENT = "order";

    private final SseEmitterRegistry registry;

    // runs before order matching, so clients get the new price before the fills it causes
    @Order(1)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceTick(PriceTickCreatedEvent event) {
        registry.broadcast(PRICE_EVENT,
                new PriceStreamEvent(event.instrumentId(), event.symbol(), event.price(), event.ts()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        registry.sendToUser(event.userId(), ORDER_EVENT,
                new OrderStreamEvent(event.orderId(), event.status(), event.filledPrice()));
    }
}
