package com.ex.tradingPlatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties("orders")
public record OrderProperties(
        // a BUY STOP fills at the market price once triggered, which may be above the stop price
        @DefaultValue("1.05") BigDecimal stopBuyReserveFactor,
        @DefaultValue("20") int maxOpenOrders
) {

    public OrderProperties {
        if (stopBuyReserveFactor.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("orders.stop-buy-reserve-factor must be at least 1");
        }
        if (maxOpenOrders < 1) {
            throw new IllegalArgumentException("orders.max-open-orders must be at least 1");
        }
    }
}
