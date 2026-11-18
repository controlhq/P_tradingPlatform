package com.ex.tradingPlatform.repositories;

import com.ex.tradingPlatform.IntegrationTest;
import com.ex.tradingPlatform.entities.Instrument;
import com.ex.tradingPlatform.entities.Order;
import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderStatus;
import com.ex.tradingPlatform.entities.OrderType;
import com.ex.tradingPlatform.entities.Position;
import com.ex.tradingPlatform.entities.Transaction;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPositionPersistenceTest extends IntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    UserRepository userRepository;

    @Autowired
    InstrumentRepository instrumentRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private User user;
    private Instrument instrument;

    @BeforeEach
    void createUserAndInstrument() {
        user = userRepository.save(new User("john@example.com", "hash", T0));
        instrument = instrumentRepository.save(new Instrument("AAPL", "Apple", T0));
    }

    @Test
    void filledMarketOrder_isPersistedWithLinkedTransaction() {
        Order order = new Order(user, instrument, OrderSide.BUY, OrderType.MARKET, 10, null, null, T0);
        order.fill(new BigDecimal("150.2500"), T0.plusSeconds(1));
        orderRepository.saveAndFlush(order);
        transactionRepository.saveAndFlush(new Transaction(user, order, TransactionType.BUY,
                new BigDecimal("1502.5000"), new BigDecimal("0.0000"), T0.plusSeconds(1)));

        Order loaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(loaded.getFilledPrice()).isEqualByComparingTo("150.25");
        assertThat(loaded.getFilledAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT order_id FROM transactions", Long.class)).isEqualTo(order.getId());
    }

    @Test
    void position_isPersistedWithAveragePrice() {
        Position position = positionRepository.saveAndFlush(
                new Position(user, instrument, 5, new BigDecimal("12.3456"), T0));

        Position loaded = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(loaded.getQuantity()).isEqualTo(5);
        assertThat(loaded.getBlockedQuantity()).isZero();
        assertThat(loaded.getAvgPrice()).isEqualByComparingTo("12.3456");
    }

    @Test
    void BR03_orderQuantityAboveLimit_isRejectedByDb() {
        assertThatThrownBy(() -> orderRepository.saveAndFlush(
                new Order(user, instrument, OrderSide.BUY, OrderType.MARKET, 10_001, null, null, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR04_limitOrderWithoutLimitPrice_isRejectedByDb() {
        assertThatThrownBy(() -> orderRepository.saveAndFlush(
                new Order(user, instrument, OrderSide.BUY, OrderType.LIMIT, 1, null, null, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR05_marketOrderWithStopPrice_isRejectedByDb() {
        assertThatThrownBy(() -> orderRepository.saveAndFlush(
                new Order(user, instrument, OrderSide.SELL, OrderType.MARKET, 1, null, BigDecimal.ONE, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR06_blockedSharesAboveQuantity_isRejectedByDb() {
        Position position = positionRepository.saveAndFlush(new Position(user, instrument, 5, BigDecimal.TEN, T0));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE positions SET blocked_quantity = 6 WHERE id = ?", position.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void BR11_emptyPosition_isRejectedByDb() {
        assertThatThrownBy(() -> positionRepository.saveAndFlush(
                new Position(user, instrument, 0, BigDecimal.TEN, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
