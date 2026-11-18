package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.Position;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.repositories.OrderRepository;
import com.ex.tradingPlatform.repositories.PositionRepository;
import com.ex.tradingPlatform.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Settles a fill the same way for MARKET and pending orders: cash, position, order status and ledger.
 * Callers run it inside their transaction and check funds or free shares beforehand.
 */
@Service
@RequiredArgsConstructor
public class TradeSettlementService {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;

    public static BigDecimal value(BigDecimal price, int quantity) {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /** Pays {@code quantity × price} from available funds and adds the shares to the position. */
    public void settleBuy(Account account, Order order, BigDecimal price, Instant now) {
        BigDecimal cost = value(price, order.getQuantity());
        account.withdraw(cost, now);
        order.fill(price, now);
        orderRepository.save(order);

        positionRepository.findByUserIdAndInstrumentId(account.getUser().getId(), order.getInstrument().getId())
                .ifPresentOrElse(
                        position -> position.add(order.getQuantity(), price, now),
                        () -> positionRepository.save(new Position(
                                account.getUser(), order.getInstrument(), order.getQuantity(), price, now)));

        transactionRepository.save(new Transaction(
                account.getUser(), order, TransactionType.BUY, cost, account.getBalance(), now));
    }

    /** Removes free shares from the position and credits {@code quantity × price}. */
    public void settleSell(Account account, Order order, Position position, BigDecimal price, Instant now) {
        BigDecimal proceeds = value(price, order.getQuantity());
        account.deposit(proceeds, now);
        order.fill(price, now);
        orderRepository.save(order);

        position.remove(order.getQuantity(), now);
        if (position.getQuantity() == 0) {
            positionRepository.delete(position);
        }

        transactionRepository.save(new Transaction(
                account.getUser(), order, TransactionType.SELL, proceeds, account.getBalance(), now));
    }
}
