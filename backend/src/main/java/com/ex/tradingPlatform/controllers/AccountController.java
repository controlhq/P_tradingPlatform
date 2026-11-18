package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.AccountResponse;
import com.ex.tradingPlatform.services.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
}
