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
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.InstrumentRepository;
import com.ex.tradingPlatform.repositories.OrderRepository;
import com.ex.tradingPlatform.repositories.PositionRepository;
import com.ex.tradingPlatform.repositories.PriceTickRepository;
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

    static final String INSTRUMENT_NOT_FOUND = "INSTRUMENT_NOT_FOUND";
    static final String INSTRUMENT_INACTIVE = "INSTRUMENT_INACTIVE";
    static final String INSTRUMENT_NOT_PRICED = "INSTRUMENT_NOT_PRICED";
    static final String MARKET_CLOSED = "MARKET_CLOSED";
    static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    static final String INSUFFICIENT_SHARES = "INSUFFICIENT_SHARES";
    static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    static final String TOO_MANY_OPEN_ORDERS = "TOO_MANY_OPEN_ORDERS";
    static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";
    private static final int MONEY_SCALE = 4;
    // id breaks ties between orders created at the same instant
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PriceTickRepository priceTickRepository;
    private final MarketHoursService marketHours;
    private final OrderProperties orderProperties;
    private final TradeSettlementService tradeSettlement;
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
        } else {
            // counted under the account lock, so concurrent requests cannot both pass the limit
            if (orderRepository.countByUserIdAndStatus(userId, OrderStatus.NEW) >= orderProperties.maxOpenOrders()) {
                throw new BusinessException(HttpStatus.CONFLICT, TOO_MANY_OPEN_ORDERS,
                        "At most " + orderProperties.maxOpenOrders() + " open orders are allowed.");
            }
            if (request.side() == OrderSide.BUY) {
                reserveFunds(account, order, now);
            } else {
                blockShares(order, now);
            }
        }
        return OrderResponse.from(order);
    }

    /**
     * Cancels a NEW order and releases what it reserved. Allowed while the market is closed,
     * because it only gives the user's own funds or shares back.
     */
    @Transactional
    public OrderResponse cancel(Long userId, Long orderId) {
        Account account = lockAccount(userId);
        Order order = orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(OrderService::orderNotFound);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessException(HttpStatus.CONFLICT, ORDER_NOT_CANCELLABLE,
                    "Only a NEW order can be cancelled; this one is " + order.getStatus() + ".");
        }

        Instant now = clock.instant();
        if (order.getSide() == OrderSide.BUY) {
            account.release(order.getReservedAmount(), now);
        } else {
            positionRepository.findByUserIdAndInstrumentId(userId, order.getInstrument().getId())
                    .orElseThrow(() -> new IllegalStateException("Pending SELL order without a position"))
                    .release(order.getQuantity(), now);
        }
        order.cancel(now);
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
                .orElseThrow(OrderService::orderNotFound);
    }

    private static BusinessException orderNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, ORDER_NOT_FOUND, "Order not found.");
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

    /** A pending SELL blocks its shares in the position; blocked shares are not free for other orders. */
    private void blockShares(Order order, Instant now) {
        Position position = freePosition(order);
        position.block(order.getQuantity(), now);
        orderRepository.save(order);
    }

    private Position freePosition(Order order) {
        return positionRepository
                .findByUserIdAndInstrumentId(order.getUser().getId(), order.getInstrument().getId())
                .filter(p -> p.getFreeQuantity() >= order.getQuantity())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_SHARES,
                        "Order quantity exceeds free shares."));
    }

    private void fillBuy(Account account, Order order, BigDecimal price, Instant now) {
        if (TradeSettlementService.value(price, order.getQuantity()).compareTo(account.getAvailableBalance()) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, INSUFFICIENT_FUNDS,
                    "Order value exceeds available funds.");
        }
        tradeSettlement.settleBuy(account, order, price, now);
    }

    private void fillSell(Account account, Order order, BigDecimal price, Instant now) {
        tradeSettlement.settleSell(account, order, freePosition(order), price, now);
    }
}
