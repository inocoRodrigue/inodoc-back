package com.esn.inodoc.service;

import com.esn.inodoc.config.JwtProperties;
import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.domain.RefreshToken;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.auth.LoginRequest;
import com.esn.inodoc.dto.auth.RefreshRequest;
import com.esn.inodoc.dto.auth.TokenResponse;
import com.esn.inodoc.dto.user.UserMapper;
import com.esn.inodoc.repo.RefreshTokenRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.JwtService;
import com.esn.inodoc.controller.error.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Authentication: account creation, login, refresh and logout.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProfileService profileService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       ProfileService profileService,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder,
                       AppProperties appProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.profileService = profileService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadRequestException("Identifiants invalides"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Identifiants invalides");
        }

        return issueTokens(user);
    }

    /**
     * Signs a user in from a verified Google account: the email domain must be
     * part of the allow-list. The account is created on first login (default
     * role INTERNAL) or reused if it already exists.
     */
    @Transactional
    public TokenResponse ssoLogin(String rawEmail, String firstName, String lastName) {
        String email = rawEmail == null ? "" : rawEmail.toLowerCase().trim();
        if (!isDomainAllowed(email)) {
            throw new BadRequestException("Ce domaine de messagerie n'est pas autorisé à se connecter");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            Role defaultRole = roleRepository.findByName(RoleName.INTERNAL)
                    .orElseThrow(() -> new IllegalStateException("Rôle par défaut introuvable"));
            user = new User();
            user.setEmail(email);
            // SSO accounts have no local password: store a random unusable hash.
            user.setPasswordHash(passwordEncoder.encode(generateRefreshTokenValue()));
            user.setFirstName(firstOrFallback(firstName, email));
            user.setLastName(lastName == null || lastName.isBlank() ? "" : lastName.trim());
            user.setEnabled(true);
            user.setRoles(new HashSet<>(Set.of(defaultRole)));
            user = userRepository.save(user);
            profileService.createEmptyProfileForUser(user);

        } else {
            if (!user.isEnabled()) {
                throw new BadRequestException("Ce compte est désactivé");
            }
            // Keep the account identity aligned with the Google profile on each
            // login, except for the administrator whose name is fixed by config.
            if (!hasAdminRole(user) && syncNamesFromGoogle(user, firstName, lastName)) {
                user = userRepository.save(user);
            }
        }

        return issueTokens(user);
    }

    private static boolean hasAdminRole(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
    }

    /** Updates the user first/last name from the Google profile; returns true if changed. */
    private static boolean syncNamesFromGoogle(User user, String firstName, String lastName) {
        boolean changed = false;
        if (firstName != null && !firstName.isBlank() && !firstName.trim().equals(user.getFirstName())) {
            user.setFirstName(firstName.trim());
            changed = true;
        }
        String normalizedLast = lastName == null ? "" : lastName.trim();
        if (!normalizedLast.isEmpty() && !normalizedLast.equals(user.getLastName())) {
            user.setLastName(normalizedLast);
            changed = true;
        }
        return changed;
    }

    private boolean isDomainAllowed(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1);
        return Arrays.stream(appProperties.getSso().getAllowedDomains().split(","))
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .anyMatch(d -> d.equalsIgnoreCase(domain));
    }

    private static String firstOrFallback(String firstName, String email) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Jeton de rafraîchissement invalide"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Jeton de rafraîchissement expiré ou révoqué");
        }

        // Rotation: revoke the old token and issue a new one.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private TokenResponse issueTokens(User user) {
        List<String> authorities = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName().name())
                .distinct()
                .toList();

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), authorities);

        String rawRefreshToken = generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenValidityDays(), ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtProperties.getAccessTokenValidityMinutes(),
                UserMapper.toDto(user));
    }

    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de hacher le jeton", e);
        }
    }
}
