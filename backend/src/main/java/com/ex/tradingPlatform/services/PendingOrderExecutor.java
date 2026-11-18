package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderStatus;
import com.ex.tradingPlatform.entities.Position;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.OrderRepository;
import com.ex.tradingPlatform.repositories.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/** Fills one pending order. A separate bean so each order gets its own transaction (REQUIRES_NEW). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOrderExecutor {

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final TradeSettlementService tradeSettlement;
    private final Clock clock;

    /**
     * Fills (or, for a BUY without enough funds, rejects) the order at {@code price} if it is still NEW
     * and triggered; returns its status afterwards.
     * The account is locked before the order is re-read, the same order as in placing and cancelling,
     * so a concurrent cancel either wins completely or sees the order already filled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderStatus execute(Long orderId, Long userId, BigDecimal price) {
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("No account for user " + userId));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " disappeared"));
        if (order.getStatus() != OrderStatus.NEW || !order.isTriggeredBy(price)) {
            return order.getStatus();
        }

        Instant now = clock.instant();
        if (order.getSide() == OrderSide.BUY) {
            fillBuy(account, order, price, now);
        } else {
            fillSell(account, order, price, now);
        }
        return order.getStatus();
    }

    /**
     * The whole reservation goes back to available funds and the actual cost is paid from them,
     * so a surplus stays with the user and a shortfall is covered from free cash. If even that is
     * not enough, the order is rejected and its reservation stays released.
     */
    private void fillBuy(Account account, Order order, BigDecimal price, Instant now) {
        account.release(order.getReservedAmount(), now);
        if (TradeSettlementService.value(price, order.getQuantity()).compareTo(account.getAvailableBalance()) > 0) {
            order.reject(now);
            log.info("Rejected order {}: not enough funds to buy {} at {}", order.getId(), order.getQuantity(), price);
            return;
        }
        tradeSettlement.settleBuy(account, order, price, now);
    }

    private void fillSell(Account account, Order order, BigDecimal price, Instant now) {
        Position position = positionRepository
                .findByUserIdAndInstrumentId(account.getUser().getId(), order.getInstrument().getId())
                .orElseThrow(() -> new IllegalStateException("Pending SELL order " + order.getId() + " without a position"));
        position.release(order.getQuantity(), now);
        tradeSettlement.settleSell(account, order, position, price, now);
    }
}
