package com.ex.tradingPlatform.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Violation of a business rule. Rendered as ProblemDetail with the given status
 * and a machine-readable {@code code} property.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
