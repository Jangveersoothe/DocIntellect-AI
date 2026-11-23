package com.jangveer.docintellect.dto;

import com.jangveer.docintellect.entity.AppUser;

import java.util.UUID;

public record UserResponse(UUID id, String email) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
