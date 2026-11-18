package com.ex.tradingPlatform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateInstrumentRequest(

        // mirrors DB check ck_instruments_symbol
        @NotNull
        @Pattern(regexp = "^[A-Z0-9.]{1,16}$", message = "must be 1-16 characters: A-Z, 0-9 or '.'")
        String symbol,

        @NotBlank
        @Size(max = 255)
        String name,

        // smallest value that is still > 0 after rounding to 4 decimal places; max fits NUMERIC(19,4)
        @NotNull
        @DecimalMin(value = "0.0001", message = "must be at least 0.0001")
        @DecimalMax(value = "999999999999999.9999")
        BigDecimal initialPrice
) {

    public CreateInstrumentRequest {
        if (symbol != null) {
            symbol = symbol.trim();
        }
        if (name != null) {
            name = name.trim();
        }
    }
}
