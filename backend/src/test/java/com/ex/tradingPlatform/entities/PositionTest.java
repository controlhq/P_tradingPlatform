package com.ex.tradingPlatform.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void BR33_buyingMore_setsQuantityWeightedAveragePrice() {
        Position position = position(10, "100");

        position.add(5, new BigDecimal("130"), T0);

        assertThat(position.getQuantity()).isEqualTo(15);
        assertThat(position.getAvgPrice()).isEqualTo(new BigDecimal("110.0000"));
    }

    @Test
    void BR33_averageAfterSeveralBuys_isRoundedHalfUpToFourPlaces() {
        Position position = position(1, "1");

        position.add(2, new BigDecimal("2"), T0);           // 5 / 3 = 1.66666...
        assertThat(position.getAvgPrice()).isEqualTo(new BigDecimal("1.6667"));

        position.add(3, new BigDecimal("10.1234"), T0);     // (3 * 1.6667 + 3 * 10.1234) / 6 = 5.89505
        assertThat(position.getQuantity()).isEqualTo(6);
        assertThat(position.getAvgPrice()).isEqualTo(new BigDecimal("5.8951"));
    }

    @Test
    void BR33_selling_keepsAveragePrice() {
        Position position = position(10, "42.5");

        position.remove(4, T0);

        assertThat(position.getQuantity()).isEqualTo(6);
        assertThat(position.getAvgPrice()).isEqualTo(new BigDecimal("42.5000"));
    }

    @Test
    void BR06_removingMoreThanFreeShares_isRefused() {
        Position position = position(3, "1");

        assertThatThrownBy(() -> position.remove(4, T0)).isInstanceOf(IllegalStateException.class);
        assertThat(position.getQuantity()).isEqualTo(3);
    }

    private static Position position(int quantity, String price) {
        return new Position(null, null, quantity, new BigDecimal(price), T0);
    }
}
