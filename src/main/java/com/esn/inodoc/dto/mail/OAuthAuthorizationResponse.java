package com.esn.inodoc.dto.mail;

/**
 * Response returned to the frontend to start the Google OAuth2 flow:
 * the consent URL to which the browser should be redirected.
 */
public record OAuthAuthorizationResponse(String authorizationUrl) {
}
