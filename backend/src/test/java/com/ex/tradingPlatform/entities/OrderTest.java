package com.ex.tradingPlatform.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @ParameterizedTest(name = "LIMIT BUY @100, price {0} -> {1}")
    @CsvSource({"99.9999, true", "100, true", "100.0000, true", "100.0001, false", "150, false"})
    void BR46_limitBuy_triggersAtOrBelowLimit(String price, boolean expected) {
        assertThat(order(OrderSide.BUY, OrderType.LIMIT, "100").isTriggeredBy(new BigDecimal(price)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "LIMIT SELL @100, price {0} -> {1}")
    @CsvSource({"100.0001, true", "100, true", "99.9999, false", "50, false"})
    void BR46_limitSell_triggersAtOrAboveLimit(String price, boolean expected) {
        assertThat(order(OrderSide.SELL, OrderType.LIMIT, "100").isTriggeredBy(new BigDecimal(price)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "STOP BUY @100, price {0} -> {1}")
    @CsvSource({"100.0001, true", "100, true", "99.9999, false", "50, false"})
    void BR46_stopBuy_triggersAtOrAboveStop(String price, boolean expected) {
        assertThat(order(OrderSide.BUY, OrderType.STOP, "100").isTriggeredBy(new BigDecimal(price)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "STOP SELL @100, price {0} -> {1}")
    @CsvSource({"99.9999, true", "100, true", "100.0001, false", "150, false"})
    void BR46_stopSell_triggersAtOrBelowStop(String price, boolean expected) {
        assertThat(order(OrderSide.SELL, OrderType.STOP, "100").isTriggeredBy(new BigDecimal(price)))
                .isEqualTo(expected);
    }

    @Test
    void BR46_marketOrder_isNeverTriggered() {
        Order market = new Order(null, null, OrderSide.BUY, OrderType.MARKET, 1, null, null, T0);

        assertThat(market.isTriggeredBy(BigDecimal.ONE)).isFalse();
    }

    private static Order order(OrderSide side, OrderType type, String price) {
        BigDecimal value = new BigDecimal(price);
        return new Order(null, null, side, type, 1,
                type == OrderType.LIMIT ? value : null,
                type == OrderType.STOP ? value : null, T0);
    }
}
