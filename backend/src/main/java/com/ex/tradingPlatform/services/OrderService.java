package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.OrderResponse;
import com.ex.tradingPlatform.dto.PlaceOrderRequest;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderType;
import com.ex.tradingPlatform.entities.Position;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.OrderRepository;
import com.ex.tradingPlatform.repositories.PositionRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
import com.ex.tradingPlatform.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderService {

    static final String ORDER_TYPE_NOT_SUPPORTED = "ORDER_TYPE_NOT_SUPPORTED";
    static final String INSTRUMENT_NOT_FOUND = "INSTRUMENT_NOT_FOUND";
    static final String INSTRUMENT_INACTIVE = "INSTRUMENT_INACTIVE";
    static final String INSTRUMENT_NOT_PRICED = "INSTRUMENT_NOT_PRICED";
    static final String MARKET_CLOSED = "MARKET_CLOSED";
    static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    static final String INSUFFICIENT_SHARES = "INSUFFICIENT_SHARES";
    static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;
    private final MarketHoursService marketHours;
    private final Clock clock;

    /**
     * Places an order and, for MARKET, fills it immediately at the latest price. The order, account,
     * position and ledger entry change in one DB transaction; a rejected order leaves no trace.
     */
    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        if (request.type() != OrderType.MARKET) {
            throw new BusinessException(HttpStatus.NOT_IMPLEMENTED, ORDER_TYPE_NOT_SUPPORTED,
                    request.type() + " orders are not supported yet.");
        }
        Instrument instrument = tradableInstrument(request.instrumentId());
        if (!marketHours.isOpen()) {
            throw new BusinessException(HttpStatus.CONFLICT, MARKET_CLOSED, "The market is closed.");
        }
        BigDecimal price = priceTickRepository.findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, INSTRUMENT_NOT_PRICED,
                        "Instrument has no price yet."))
                .getPrice();

        // locking the account first serializes all order executions of one user
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND, "Account not found."));
        Instant now = clock.instant();
        Order order = new Order(account.getUser(), instrument, request.side(), OrderType.MARKET,
                request.quantity(), null, null, now);

        if (request.side() == OrderSide.BUY) {
            fillBuy(account, order, price, now);
        } else {
            fillSell(account, order, price, now);
        }
        return OrderResponse.from(order);
    }

    private Instrument tradableInstrument(Long instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, INSTRUMENT_NOT_FOUND,
                        "Instrument not found."));
        if (!instrument.isActive()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, INSTRUMENT_INACTIVE,
                    "Instrument is not active.");
        }
        return instrument;
    }

    private void fillBuy(Account account, Order order, BigDecimal price, Instant now) {
        BigDecimal cost = price.multiply(BigDecimal.valueOf(order.getQuantity()));
        if (cost.compareTo(account.getAvailableBalance()) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_FUNDS,
                    "Order value exceeds available funds.");
        }

        account.withdraw(cost, now);
        order.fill(price, now);
        orderRepository.save(order);

        Long userId = account.getUser().getId();
        Long instrumentId = order.getInstrument().getId();
        positionRepository.findByUserIdAndInstrumentId(userId, instrumentId).ifPresentOrElse(
                position -> position.add(order.getQuantity(), price, now),
                () -> positionRepository.save(
                        new Position(account.getUser(), order.getInstrument(), order.getQuantity(), price, now)));

        transactionRepository.save(new Transaction(
                account.getUser(), order, TransactionType.BUY, cost, account.getBalance(), now));
    }

    private void fillSell(Account account, Order order, BigDecimal price, Instant now) {
        Position position = positionRepository
                .findByUserIdAndInstrumentId(account.getUser().getId(), order.getInstrument().getId())
                .filter(p -> p.getFreeQuantity() >= order.getQuantity())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_SHARES,
                        "Order quantity exceeds free shares."));

        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(order.getQuantity()));
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
