package com.ex.tradingPlatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        // regexp mirrors DB check ck_users_email_format (domain must contain a dot)
        @NotBlank
        @Size(max = 254)
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        String email,

        @NotNull
        @Size(min = 8, max = 128)
        @Pattern(regexp = "(?s)^(?=.*\\p{L})(?=.*\\d).*$",
                message = "must contain at least one letter and one digit")
        String password
) {

    // @Email rejects surrounding whitespace, so strip it before validation runs
    public RegisterRequest {
        if (email != null) {
            email = email.trim();
        }
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
