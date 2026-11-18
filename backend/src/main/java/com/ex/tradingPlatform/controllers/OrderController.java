package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.OrderResponse;
import com.ex.tradingPlatform.dto.PlaceOrderRequest;
import com.ex.tradingPlatform.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order; a MARKET order is filled immediately at the latest price")
    @ApiResponse(responseCode = "201", description = "Order placed (MARKET: already FILLED)")
    @ApiResponse(responseCode = "400", description = "Validation failed, incl. prices not matching the order type")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Instrument does not exist (INSTRUMENT_NOT_FOUND)")
    @ApiResponse(responseCode = "409",
            description = "Market closed (MARKET_CLOSED), not enough funds (INSUFFICIENT_FUNDS) "
                    + "or free shares (INSUFFICIENT_SHARES)")
    @ApiResponse(responseCode = "422", description = "Instrument is inactive (INSTRUMENT_INACTIVE)")
    @ApiResponse(responseCode = "501", description = "Order type not supported yet (ORDER_TYPE_NOT_SUPPORTED)")
    public OrderResponse place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(Long.valueOf(jwt.getSubject()), request);
    }
}
