package com.esn.inodoc.dto.auth;

/**
 * Google consent URL returned to the frontend to start a SSO login.
 */
public record GoogleLoginUrlResponse(String authorizationUrl) {
}
