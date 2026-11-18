package com.ex.tradingPlatform.controllers;

import com.ex.tradingPlatform.services.StreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
@Tag(name = "Stream")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    @GetMapping
    @Operation(summary = "Server-Sent Events: live prices and status changes of the caller's orders")
    @ApiResponse(responseCode = "200", description = "text/event-stream with 'price' and 'order' events")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token (INVALID_TOKEN)")
    public SseEmitter stream(
            @Parameter(description = "Access token; EventSource cannot send an Authorization header")
            @RequestParam(required = false) String token) {
        return streamService.subscribe(token);
    }
}
