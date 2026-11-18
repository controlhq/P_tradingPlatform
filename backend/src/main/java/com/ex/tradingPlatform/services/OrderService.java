package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.config.OrderProperties;
import com.ex.tradingPlatform.dto.OrderResponse;
import com.ex.tradingPlatform.dto.PageResponse;
import com.ex.tradingPlatform.dto.PlaceOrderRequest;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    private static final int MONEY_SCALE = 4;
    // id breaks ties between orders created at the same instant
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;
    private final MarketHoursService marketHours;
    private final OrderProperties orderProperties;
    private final Clock clock;

    /**
     * Places an order. MARKET is filled immediately at the latest price; LIMIT and STOP stay NEW and
     * reserve what they may later need. Everything changes in one DB transaction, so a rejected order
     * leaves no trace.
     */
    @Transactional
    public OrderResponse place(Long userId, PlaceOrderRequest request) {
        Instrument instrument = tradableInstrument(request.instrumentId());
        if (!marketHours.isOpen()) {
            throw new BusinessException(HttpStatus.CONFLICT, MARKET_CLOSED, "The market is closed.");
        }

        // locking the account first serializes all order operations of one user
        Account account = lockAccount(userId);
        Instant now = clock.instant();
        Order order = new Order(account.getUser(), instrument, request.side(), request.type(),
                request.quantity(), request.limitPrice(), request.stopPrice(), now);

        if (request.type() == OrderType.MARKET) {
            BigDecimal price = latestPrice(instrument);
            if (request.side() == OrderSide.BUY) {
                fillBuy(account, order, price, now);
            } else {
                fillSell(account, order, price, now);
            }
        } else if (request.side() == OrderSide.BUY) {
            reserveFunds(account, order, now);
        } else {
            throw new BusinessException(HttpStatus.NOT_IMPLEMENTED, ORDER_TYPE_NOT_SUPPORTED,
                    "SELL " + request.type() + " orders are not supported yet.");
        }
        return OrderResponse.from(order);
    }

    /** The user's own orders, newest first; {@code status} is an optional filter. */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long userId, OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Order> result = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(result.map(OrderResponse::from));
    }

    /** Another user's order is reported as not found, so ids of foreign orders are not revealed. */
    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, ORDER_NOT_FOUND, "Order not found."));
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

    private Account lockAccount(Long userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, ACCOUNT_NOT_FOUND, "Account not found."));
    }

    private BigDecimal latestPrice(Instrument instrument) {
        return priceTickRepository.findFirstByInstrumentIdOrderByTsDescIdDesc(instrument.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, INSTRUMENT_NOT_PRICED,
                        "Instrument has no price yet."))
                .getPrice();
    }

    /** BUY LIMIT reserves quantity x limit price; BUY STOP reserves quantity x stop price x buffer factor. */
    private void reserveFunds(Account account, Order order, Instant now) {
        BigDecimal quantity = BigDecimal.valueOf(order.getQuantity());
        BigDecimal amount = order.getType() == OrderType.LIMIT
                ? order.getLimitPrice().multiply(quantity)
                : order.getStopPrice().multiply(quantity).multiply(orderProperties.stopBuyReserveFactor());
        amount = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amount.compareTo(account.getAvailableBalance()) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_FUNDS,
                    "Reservation exceeds available funds.");
        }

        account.reserve(amount, now);
        order.reserveFunds(amount);
        orderRepository.save(order);
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
