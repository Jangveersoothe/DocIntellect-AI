package com.jangveer.docintellect.dto;

public record AuthTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
