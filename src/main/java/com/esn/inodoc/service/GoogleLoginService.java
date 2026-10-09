package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.dto.auth.TokenResponse;
import com.esn.inodoc.service.GoogleOAuthService.GoogleProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Google single sign-on flow: builds the consent URL and completes the login
 * by exchanging the authorization code and issuing the application tokens.
 */
@Service
public class GoogleLoginService {

    private final GoogleOAuthService googleOAuthService;
    private final AuthService authService;
    private final AppProperties appProperties;

    public GoogleLoginService(GoogleOAuthService googleOAuthService,
                              AuthService authService,
                              AppProperties appProperties) {
        this.googleOAuthService = googleOAuthService;
        this.authService = authService;
        this.appProperties = appProperties;
    }

    /** Builds the Google consent URL for a new login (no user yet). */
    public String beginLogin() {
        String state = googleOAuthService.signState(UUID.randomUUID());
        return googleOAuthService.buildLoginUrl(redirectUri(), state);
    }

    /** Verifies the callback, retrieves the Google profile and issues tokens. */
    @Transactional
    public TokenResponse completeLogin(String code, String state) {
        // Verifies signature and expiry (CSRF protection); the user id is a nonce.
        googleOAuthService.verifyState(state);
        GoogleProfile profile = googleOAuthService.exchangeLoginCode(redirectUri(), code);
        return authService.ssoLogin(profile.email(), profile.firstName(), profile.lastName());
    }

    private String redirectUri() {
        return appProperties.getSso().getGoogle().getLoginRedirectUri();
    }
}
