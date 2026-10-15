package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.dto.mail.MailAccountDto;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.service.GoogleOAuthService.ConnectedGoogleAccount;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Management of connected Google accounts (OAuth2) used to send emails.
 */
@Service
public class MailAccountService {

    private final MailAccountRepository repository;
    private final MailCryptoService cryptoService;
    private final GoogleOAuthService googleOAuthService;
    private final UserRepository users;

    public MailAccountService(MailAccountRepository repository, MailCryptoService cryptoService,
                              GoogleOAuthService googleOAuthService, UserRepository users) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.googleOAuthService = googleOAuthService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MailAccountDto> list() {
        return repository.findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(
                SecurityUtils.requirePrincipal().getUserId()).stream().map(this::toDto).toList();
    }

    public boolean hasUsableConnection(UUID userId) {
        MailAccount account = repository.findByCreatedByAndActiveTrue(userId).orElse(null);
        if (account == null) return false;
        try {
            String refreshToken = cryptoService.decrypt(account.getRefreshTokenEncrypted());
            String accessToken = googleOAuthService.refreshAccessToken(refreshToken);
            return account.getEmail().equalsIgnoreCase(googleOAuthService.fetchEmail(accessToken));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Starts the OAuth flow: returns the Google consent URL for
     * the current authenticated user.
     */
    @Transactional(readOnly = true)
    public String beginAuthorization() {
        UUID userId = SecurityUtils.getCurrentPrincipal()
                .map(p -> p.getUserId())
                .orElseThrow(() -> new NotFoundException("Utilisateur non authentifié"));
        return googleOAuthService.buildAuthorizationUrl(googleOAuthService.signState(userId));
    }

    /**
     * Completes the OAuth flow: verifies the state, exchanges the code for the
     * Google tokens and saves (or reconnects) the account with its encrypted refresh token.
     */
    @Transactional
    public void completeAuthorization(String code, String state) {
        UUID userId = googleOAuthService.verifyState(state);
        users.findById(userId).filter(user -> user.isEnabled())
                .orElseThrow(() -> new AccessDeniedException("Compte utilisateur indisponible"));
        ConnectedGoogleAccount google = googleOAuthService.exchangeCode(code);
        String email = google.email();

        MailAccount account = repository.findByEmailIgnoreCase(email).orElseGet(MailAccount::new);
        if (account.getCreatedBy() != null && !userId.equals(account.getCreatedBy())) {
            throw new ConflictException("Ce compte Google est déjà associé à un autre utilisateur");
        }
        repository.findByCreatedByAndActiveTrue(userId)
                .filter(existing -> !existing.getEmail().equalsIgnoreCase(email))
                .ifPresent(existing -> {
                    throw new ConflictException("Déconnectez votre compte Google actuel avant d'en connecter un autre");
                });
        account.setEmail(email);
        if (account.getDisplayName() == null) {
            account.setDisplayName(email);
        }
        account.setRefreshTokenEncrypted(cryptoService.encrypt(google.refreshToken()));
        account.setActive(true);
        if (account.getCreatedBy() == null) {
            account.setCreatedBy(userId);
        }
        repository.save(account);
    }

    /** Stores the Gmail grant returned as part of the initial SSO consent. */
    @Transactional
    public void connectFromSso(UUID userId, String email, String refreshToken) {
        if (email == null || email.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        MailAccount active = repository.findByCreatedByAndActiveTrue(userId).orElse(null);
        if (active != null) {
            if (active.getEmail().equalsIgnoreCase(email)) {
                active.setRefreshTokenEncrypted(cryptoService.encrypt(refreshToken));
                repository.save(active);
            }
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        MailAccount account = repository.findByEmailIgnoreCase(normalizedEmail).orElseGet(MailAccount::new);
        if (account.getCreatedBy() != null && !userId.equals(account.getCreatedBy())) {
            return;
        }
        account.setEmail(normalizedEmail);
        if (account.getDisplayName() == null) {
            account.setDisplayName(normalizedEmail);
        }
        account.setRefreshTokenEncrypted(cryptoService.encrypt(refreshToken));
        account.setActive(true);
        account.setCreatedBy(userId);
        repository.save(account);
    }

    @Transactional
    public void delete(UUID id) {
        MailAccount account = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Compte email introuvable"));
        assertOwner(account);
        repository.delete(account);
    }

    @Transactional(readOnly = true)
    public MailAccount requireActive(UUID id) {
        MailAccount account = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Compte email introuvable"));
        assertOwner(account);
        if (!account.isActive()) {
            throw new NotFoundException("Compte email desactive");
        }
        return account;
    }

    public void assertOwner(MailAccount account) {
        if (!SecurityUtils.requirePrincipal().getUserId().equals(account.getCreatedBy())) {
            throw new AccessDeniedException("Ce compte Google ne vous appartient pas");
        }
    }

    private MailAccountDto toDto(MailAccount account) {
        return new MailAccountDto(account.getId(), account.getEmail(),
                account.getDisplayName(), account.isActive());
    }
}
