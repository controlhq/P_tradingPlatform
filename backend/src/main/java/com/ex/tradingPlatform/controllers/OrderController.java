package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.OrderResponse;
import com.ex.tradingPlatform.dto.PageResponse;
import com.ex.tradingPlatform.dto.PlaceOrderRequest;
import com.ex.tradingPlatform.entities.OrderStatus;
import com.ex.tradingPlatform.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                    + "or free shares (INSUFFICIENT_SHARES), too many open orders (TOO_MANY_OPEN_ORDERS)")
    @ApiResponse(responseCode = "422", description = "Instrument is inactive (INSTRUMENT_INACTIVE)")    public OrderResponse place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(Long.valueOf(jwt.getSubject()), request);
    }

    @GetMapping
    @Operation(summary = "List the current user's orders, newest first")
    @ApiResponse(responseCode = "200", description = "One page of orders")
    @ApiResponse(responseCode = "400", description = "page < 0, size not in 1-100 or unknown status")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Only orders with this status")
            @RequestParam(required = false) OrderStatus status) {
        return orderService.list(Long.valueOf(jwt.getSubject()), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the current user's orders")
    @ApiResponse(responseCode = "200", description = "Order")
    @ApiResponse(responseCode = "400", description = "Id is not a number")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "No such order of this user (ORDER_NOT_FOUND)")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return orderService.get(Long.valueOf(jwt.getSubject()), id);
    }
}
