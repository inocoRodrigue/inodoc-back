package com.esn.inodoc.dto.auth;

import com.esn.inodoc.dto.user.UserDto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMinutes,
        UserDto user) {
}
