package com.ex.tradingPlatform.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateInstrumentRequest(

        @NotNull
        Boolean active
) {
}
