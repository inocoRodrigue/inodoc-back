package com.esn.inodoc.controller;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.dto.auth.GoogleLoginUrlResponse;
import com.esn.inodoc.dto.auth.LoginRequest;
import com.esn.inodoc.dto.auth.RefreshRequest;
import com.esn.inodoc.dto.auth.TokenResponse;
import com.esn.inodoc.service.AuthService;
import com.esn.inodoc.service.GoogleLoginService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleLoginService googleLoginService;
    private final AppProperties appProperties;

    public AuthController(AuthService authService,
                          GoogleLoginService googleLoginService,
                          AppProperties appProperties) {
        this.authService = authService;
        this.googleLoginService = googleLoginService;
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/google/login-url")
    public GoogleLoginUrlResponse googleLoginUrl() {
        return new GoogleLoginUrlResponse(googleLoginService.beginLogin());
    }

    /** OAuth2 callback called by Google; redirects to the frontend with the tokens. */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(@RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state,
                                               @RequestParam(required = false) String error) {
        String base = appProperties.getSso().getGoogle().getSuccessRedirect();
        String target;
        if (error != null) {
            target = base + "?error=" + encode("Connexion Google refusée");
        } else if (code == null || state == null) {
            target = base + "?error=" + encode("Réponse Google incomplète");
        } else {
            try {
                TokenResponse tokens = googleLoginService.completeLogin(code, state);
                // Tokens are passed in the URL fragment (never sent to servers/logs).
                target = base + "#accessToken=" + encode(tokens.accessToken())
                        + "&refreshToken=" + encode(tokens.refreshToken());
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "Échec de la connexion Google";
                target = base + "?error=" + encode(message);
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
