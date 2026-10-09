package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.controller.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates the OAuth2 exchanges with Google: building the consent
 * URL, exchanging the code for tokens, refreshing the access token and
 * retrieving the account email address.
 *
 * <p>The OAuth state ({@code state} parameter) is signed with HMAC-SHA256 in
 * order to verify, during the callback, that the flow was indeed initiated by
 * an authenticated user (CSRF protection) and has not expired.</p>
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SCOPES =
            "openid email https://www.googleapis.com/auth/gmail.send";
    private static final String LOGIN_SCOPES = "openid email profile";
    private static final long STATE_TTL_MS = 10 * 60 * 1000L;

    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** Connected Google account, result of a successful code exchange. */
    public record ConnectedGoogleAccount(String email, String refreshToken) {
    }

    /** Google account profile retrieved during a SSO login. */
    public record GoogleProfile(String email, String firstName, String lastName) {
    }

    private AppProperties.Mail.Google config() {
        AppProperties.Mail.Google google = appProperties.getMail().getGoogle();
        if (google.getClientId() == null || google.getClientId().isBlank()
                || google.getClientSecret() == null || google.getClientSecret().isBlank()) {
            throw new BadRequestException(
                    "L'intégration Google n'est pas configurée (client id/secret manquants)");
        }
        return google;
    }

    /** Builds the Google consent URL for a given state. */
    public String buildAuthorizationUrl(String state) {
        AppProperties.Mail.Google google = config();
        return UriComponentsBuilder.fromHttpUrl(AUTH_ENDPOINT)
                .queryParam("client_id", google.getClientId())
                .queryParam("redirect_uri", google.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    /**
     * Exchanges the authorization code for Google tokens and retrieves
     * the associated email address.
     */
    public ConnectedGoogleAccount exchangeCode(String code) {
        AppProperties.Mail.Google google = config();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("redirect_uri", google.getRedirectUri());
        form.add("grant_type", "authorization_code");

        Map<String, Object> body = postForm(form);
        String accessToken = asString(body.get("access_token"));
        String refreshToken = asString(body.get("refresh_token"));
        if (accessToken == null) {
            throw new BadRequestException("Réponse Google invalide (access token manquant)");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException(
                    "Google n'a pas renvoyé de refresh token. Révoquez l'accès de l'application "
                    + "dans votre compte Google puis réessayez.");
        }
        String email = fetchEmail(accessToken);
        return new ConnectedGoogleAccount(email, refreshToken);
    }

    /**
     * Builds the Google consent URL for a SSO login, using the dedicated login
     * redirect URI and profile-only scopes (no offline access, no Gmail scope).
     */
    public String buildLoginUrl(String redirectUri, String state) {
        AppProperties.Mail.Google google = config();
        return UriComponentsBuilder.fromHttpUrl(AUTH_ENDPOINT)
                .queryParam("client_id", google.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", LOGIN_SCOPES)
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "select_account")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    /**
     * Exchanges the SSO authorization code for a Google access token and
     * retrieves the account profile (email, first and last name).
     */
    public GoogleProfile exchangeLoginCode(String redirectUri, String code) {
        AppProperties.Mail.Google google = config();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        Map<String, Object> body = postForm(form);
        String accessToken = asString(body.get("access_token"));
        if (accessToken == null) {
            throw new BadRequestException("Réponse Google invalide (access token manquant)");
        }
        return fetchProfile(accessToken);
    }

    /** Obtains a fresh access token from a refresh token. */
    public String refreshAccessToken(String refreshToken) {
        AppProperties.Mail.Google google = config();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", google.getClientId());
        form.add("client_secret", google.getClientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        Map<String, Object> body = postForm(form);
        String accessToken = asString(body.get("access_token"));
        if (accessToken == null) {
            throw new BadRequestException("Impossible de rafraîchir l'access token Google");
        }
        return accessToken;
    }

    public String fetchEmail(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = restClient.get()
                    .uri(USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            String email = info == null ? null : asString(info.get("email"));
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Impossible de récupérer l'adresse email Google");
            }
            if (!Boolean.TRUE.equals(info.get("email_verified"))) {
                throw new BadRequestException("L'adresse email Google n'est pas vérifiée");
            }
            return email.toLowerCase().trim();
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("Échec de récupération du profil Google");
            throw new BadRequestException("Impossible de récupérer l'adresse email Google");
        }
    }

    private GoogleProfile fetchProfile(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = restClient.get()
                    .uri(USERINFO_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            String email = info == null ? null : asString(info.get("email"));
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Impossible de récupérer l'adresse email Google");
            }
            String firstName = info == null ? null : asString(info.get("given_name"));
            String lastName = info == null ? null : asString(info.get("family_name"));
            return new GoogleProfile(email.toLowerCase().trim(), firstName, lastName);
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("Échec de récupération du profil Google");
            throw new BadRequestException("Impossible de récupérer le profil Google");
        }
    }

    private Map<String, Object> postForm(MultiValueMap<String, String> form) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new BadRequestException("Réponse vide du serveur OAuth Google");
            }
            return body;
        } catch (BadRequestException e) {
            throw e;
        } catch (org.springframework.web.client.RestClientException e) {
            // Never log the client secret or the tokens.
            log.warn("Échec de l'échange OAuth Google");
            throw new BadRequestException("Échec de l'authentification Google");
        }
    }

    // --- OAuth state signing (HMAC-SHA256) ---

    /** Signs a state containing the user id and an expiration date. */
    public String signState(UUID userId) {
        String payload = userId + ":" + (System.currentTimeMillis() + STATE_TTL_MS);
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + hmac(encodedPayload);
    }

    /** Verifies the signature and expiration of the state, then returns the initiating user. */
    public UUID verifyState(String state) {
        if (state == null || !state.contains(".")) {
            throw new BadRequestException("État OAuth invalide");
        }
        int dot = state.lastIndexOf('.');
        String encodedPayload = state.substring(0, dot);
        String signature = state.substring(dot + 1);
        if (!constantTimeEquals(hmac(encodedPayload), signature)) {
            throw new BadRequestException("Signature de l'état OAuth invalide");
        }
        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        int sep = payload.lastIndexOf(':');
        if (sep < 0) {
            throw new BadRequestException("État OAuth invalide");
        }
        long expiry = Long.parseLong(payload.substring(sep + 1));
        if (System.currentTimeMillis() > expiry) {
            throw new BadRequestException("La demande de connexion a expiré, réessayez");
        }
        return UUID.fromString(payload.substring(0, sep));
    }

    private String hmac(String data) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(appProperties.getMail().getSecretKey().getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de signer l'état OAuth", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
