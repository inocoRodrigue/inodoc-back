package com.esn.inodoc.controller;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.dto.mail.MailAccountDto;
import com.esn.inodoc.dto.mail.OAuthAuthorizationResponse;
import com.esn.inodoc.service.MailAccountService;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Management of connected Google accounts (OAuth2) for sending emails.
 * Connections are personal. The public callback requires the signed, expiring,
 * single-use state stored in the initiating browser session.
 */
@RestController
@RequestMapping("/mail-accounts")
public class MailAccountController {
    private static final String OAUTH_STATE = "inodoc.mail.oauth-state";

    private final MailAccountService mailAccountService;
    private final AppProperties appProperties;

    public MailAccountController(MailAccountService mailAccountService, AppProperties appProperties) {
        this.mailAccountService = mailAccountService;
        this.appProperties = appProperties;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MailAccountDto> list() {
        return mailAccountService.list();
    }

    /** Starts the Google OAuth2 flow and returns the consent URL. */
    @GetMapping("/oauth/authorize")
    @PreAuthorize("isAuthenticated()")
    public OAuthAuthorizationResponse authorize(HttpSession session) {
        String url = mailAccountService.beginAuthorization();
        String state = org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
                .build().getQueryParams().getFirst("state");
        session.setAttribute(OAUTH_STATE, state);
        return new OAuthAuthorizationResponse(url);
    }

    /** OAuth2 callback called by Google; redirects to the frontend. */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpSession session) {
        String base = appProperties.getMail().getGoogle().getSuccessRedirect();
        String target;
        Object expected = session.getAttribute(OAUTH_STATE);
        session.removeAttribute(OAUTH_STATE);
        if (!(expected instanceof String expectedState) || state == null
                || !java.security.MessageDigest.isEqual(
                        expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            target = base + "?mailError=" + encode("Session de connexion Google invalide ou expirée");
        } else if (error != null) {
            target = base + "?mailError=" + encode("Autorisation Google refusée");
        } else if (code == null || state == null) {
            target = base + "?mailError=" + encode("Reponse Google incomplete");
        } else {
            try {
                mailAccountService.completeAuthorization(code, state);
                target = base + "?mail=connected";
            } catch (BadRequestException | ConflictException | AccessDeniedException
                     | DataIntegrityViolationException e) {
                target = base + "?mailError=" + encode("Échec de la connexion Google. Vérifiez le compte et réessayez.");
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mailAccountService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
