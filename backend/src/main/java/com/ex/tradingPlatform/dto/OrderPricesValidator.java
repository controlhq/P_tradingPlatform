package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.OrderType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/** Reports violations on the price fields themselves, so the API returns them as field errors. */
public class OrderPricesValidator implements ConstraintValidator<ValidOrderPrices, PlaceOrderRequest> {

    @Override
    public boolean isValid(PlaceOrderRequest request, ConstraintValidatorContext context) {
        if (request == null || request.type() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean limitOk = check(context, "limitPrice", request.limitPrice(), request.type(), OrderType.LIMIT);
        boolean stopOk = check(context, "stopPrice", request.stopPrice(), request.type(), OrderType.STOP);
        return limitOk && stopOk;
    }

    private static boolean check(ConstraintValidatorContext context, String field, BigDecimal value,
                                 OrderType type, OrderType requiredFor) {
        String message;
        if (type == requiredFor && value == null) {
            message = "is required for " + requiredFor + " orders";
        } else if (type != requiredFor && value != null) {
            message = "must be empty for " + type + " orders";
        } else {
            return true;
        }
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        return false;
    }
}
