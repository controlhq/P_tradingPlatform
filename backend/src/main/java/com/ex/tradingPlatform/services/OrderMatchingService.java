package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderStatus;
import com.ex.tradingPlatform.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

/**
 * Matches pending LIMIT/STOP orders against a new price of an instrument. Deliberately not transactional:
 * every order is executed in its own transaction, so one failing order does not block the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private final OrderRepository orderRepository;
    private final PendingOrderExecutor executor;

    /**
     * Runs after the tick is committed, so orders are never filled at a price that was rolled back;
     * ordered after the live price broadcast (the entity named Order forces the qualified annotation).
     */
    @org.springframework.core.annotation.Order(2)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceTickCreated(PriceTickCreatedEvent event) {
        onPriceTick(event.instrumentId(), event.price());
    }

    /** Processes triggered NEW orders oldest first; returns how many were filled. */
    public int onPriceTick(Long instrumentId, BigDecimal price) {
        List<Order> pending = orderRepository.findByInstrumentIdAndStatusOrderByCreatedAtAscIdAsc(
                instrumentId, OrderStatus.NEW);

        int filled = 0;
        for (Order order : pending) {
            if (!order.isTriggeredBy(price)) {
                continue;
            }
            try {
                if (executor.execute(order.getId(), order.getUser().getId(), price) == OrderStatus.FILLED) {
                    filled++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not execute order {} at price {}", order.getId(), price, ex);
            }
        }
        return filled;
    }
}
