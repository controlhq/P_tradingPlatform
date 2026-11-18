package com.ex.tradingPlatform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AmountRequest(

        // limit per single deposit or withdrawal; at most 4 decimal places to fit NUMERIC(19,4)
        @NotNull
        @Positive
        @DecimalMax(value = "1000000", message = "must be at most 1000000")
        @Digits(integer = 7, fraction = 4, message = "must have at most 4 decimal places")
        BigDecimal amount
) {
}
