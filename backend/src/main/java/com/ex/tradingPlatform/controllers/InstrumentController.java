package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.InstrumentResponse;
import com.ex.tradingPlatform.services.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instruments")
@Tag(name = "Instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentService instrumentService;

    @GetMapping
    @Operation(summary = "List active instruments with their latest price")
    @ApiResponse(responseCode = "200", description = "Active instruments ordered by symbol")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    public List<InstrumentResponse> list() {
        return instrumentService.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get instrument details with its latest price")
    @ApiResponse(responseCode = "200", description = "Instrument (also returned when inactive)")
    @ApiResponse(responseCode = "400", description = "Id is not a number")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Instrument does not exist (INSTRUMENT_NOT_FOUND)")
    public InstrumentResponse get(@PathVariable Long id) {
        return instrumentService.getById(id);
    }
}
