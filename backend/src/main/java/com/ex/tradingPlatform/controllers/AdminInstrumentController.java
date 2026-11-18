package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.dto.CreateInstrumentRequest;
import com.ex.tradingPlatform.dto.InstrumentResponse;
import com.ex.tradingPlatform.dto.UpdateInstrumentRequest;
import com.ex.tradingPlatform.services.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/instruments")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
@RequiredArgsConstructor
public class AdminInstrumentController {

    private final InstrumentService instrumentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an instrument with its starting price")
    @ApiResponse(responseCode = "201", description = "Instrument created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    @ApiResponse(responseCode = "409", description = "Symbol already exists (SYMBOL_TAKEN)")
    public InstrumentResponse create(@Valid @RequestBody CreateInstrumentRequest request) {
        return instrumentService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Activate or deactivate an instrument")
    @ApiResponse(responseCode = "200", description = "Instrument updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    @ApiResponse(responseCode = "404", description = "Instrument does not exist (INSTRUMENT_NOT_FOUND)")
    public InstrumentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateInstrumentRequest request) {
        return instrumentService.update(id, request);
    }
}
