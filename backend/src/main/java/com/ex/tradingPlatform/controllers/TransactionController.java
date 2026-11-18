package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.PageResponse;
import com.ex.tradingPlatform.dto.TransactionResponse;
import com.ex.tradingPlatform.entities.TransactionType;
import com.ex.tradingPlatform.services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Account")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @Operation(summary = "List the current user's cash transactions, newest first")
    @ApiResponse(responseCode = "200", description = "One page of transactions")
    @ApiResponse(responseCode = "400", description = "page < 0, size not in 1-100 or unknown type")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Only transactions of this type")
            @RequestParam(required = false) TransactionType type) {
        return transactionService.list(Long.valueOf(jwt.getSubject()), type, page, size);
    }
}
