package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.AccountResponse;
import com.ex.tradingPlatform.dto.AmountRequest;
import com.ex.tradingPlatform.services.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Get the current user's cash account")
    @ApiResponse(responseCode = "200", description = "Balance, blocked funds and available funds")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Account no longer exists (ACCOUNT_NOT_FOUND)")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getForUser(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit cash into the current user's account")
    @ApiResponse(responseCode = "200", description = "Account after the deposit")
    @ApiResponse(responseCode = "400", description = "Amount not in (0, 1000000] or more than 4 decimal places")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public AccountResponse deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest request) {
        return accountService.deposit(Long.valueOf(jwt.getSubject()), request.amount());
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw cash from the available funds of the current user's account")
    @ApiResponse(responseCode = "200", description = "Account after the withdrawal")
    @ApiResponse(responseCode = "400", description = "Amount not in (0, 1000000] or more than 4 decimal places")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "409", description = "Amount exceeds available funds (INSUFFICIENT_FUNDS)")
    public AccountResponse withdraw(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AmountRequest request) {
        return accountService.withdraw(Long.valueOf(jwt.getSubject()), request.amount());
    }
}
