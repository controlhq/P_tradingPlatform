package com.ex.tradingPlatform.dto;

import com.ex.tradingPlatform.entities.Role;
import com.ex.tradingPlatform.entities.User;

public record UserResponse(Long id, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
