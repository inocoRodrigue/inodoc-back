package com.esn.inodoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres JWT (prefixe inodoc.security.jwt).
 */
@ConfigurationProperties(prefix = "inodoc.security.jwt")
public class JwtProperties {

    /** Secret key encoded in Base64 (>= 256 bits). */
    private String secret;

    private long accessTokenValidityMinutes = 30;

    private long refreshTokenValidityDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenValidityMinutes() {
        return accessTokenValidityMinutes;
    }

    public void setAccessTokenValidityMinutes(long accessTokenValidityMinutes) {
        this.accessTokenValidityMinutes = accessTokenValidityMinutes;
    }

    public long getRefreshTokenValidityDays() {
        return refreshTokenValidityDays;
    }

    public void setRefreshTokenValidityDays(long refreshTokenValidityDays) {
        this.refreshTokenValidityDays = refreshTokenValidityDays;
    }
}
