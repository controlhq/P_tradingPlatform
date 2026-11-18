package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.MarketStatusResponse;
import com.ex.tradingPlatform.services.MarketHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketHoursService marketHoursService;

    @GetMapping("/status")
    @Operation(summary = "Get whether the market is open and when that changes next")
    @ApiResponse(responseCode = "200", description = "Market status; nextChange is null when always open")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public MarketStatusResponse status() {
        return marketHoursService.status();
    }
}
