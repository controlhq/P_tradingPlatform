package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.OrderSide;
import com.ex.tradingPlatform.entities.OrderType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@ValidOrderPrices
public record PlaceOrderRequest(

        @NotNull
        OrderSide side,

        @NotNull
        OrderType type,

        @NotNull
        @Positive
        Long instrumentId,

        // mirrors DB check ck_orders_quantity
        @NotNull
        @Min(1)
        @Max(10000)
        Integer quantity,

        @DecimalMin(value = "0.0001", message = "must be at least 0.0001")
        @DecimalMax("999999999999999.9999")
        @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal limitPrice,

        @DecimalMin(value = "0.0001", message = "must be at least 0.0001")
        @DecimalMax("999999999999999.9999")
        @Digits(integer = 15, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal stopPrice
) {
}
