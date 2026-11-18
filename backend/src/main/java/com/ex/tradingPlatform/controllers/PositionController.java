package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.PositionResponse;
import com.ex.tradingPlatform.services.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@Tag(name = "Positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @Operation(summary = "List the current user's positions valued at the latest prices")
    @ApiResponse(responseCode = "200", description = "Positions ordered by symbol")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public List<PositionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return positionService.listForUser(Long.valueOf(jwt.getSubject()));
    }
}
