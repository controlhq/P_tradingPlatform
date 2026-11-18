package com.ex.tradingPlatform.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * LIMIT requires limitPrice, STOP requires stopPrice, and each price is allowed only for its own type
 * (mirrors DB checks ck_orders_limit_price and ck_orders_stop_price).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderPricesValidator.class)
public @interface ValidOrderPrices {

    String message() default "prices do not match the order type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
