package com.ex.tradingPlatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties("orders")
public record OrderProperties(
        // a BUY STOP fills at the market price once triggered, which may be above the stop price
        @DefaultValue("1.05") BigDecimal stopBuyReserveFactor
) {

    public OrderProperties {
        if (stopBuyReserveFactor.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("orders.stop-buy-reserve-factor must be at least 1");
        }
    }
}
