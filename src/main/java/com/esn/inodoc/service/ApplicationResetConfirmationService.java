package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ApplicationResetConfirmationService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final MailAccountRepository accounts;
    private final EmailService email;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public ApplicationResetConfirmationService(JdbcTemplate jdbc, UserRepository users,
                                               MailAccountRepository accounts, EmailService email,
                                               PasswordEncoder encoder) {
        this(jdbc, users, accounts, email, encoder, Clock.systemUTC());
    }

    ApplicationResetConfirmationService(JdbcTemplate jdbc, UserRepository users,
                                        MailAccountRepository accounts, EmailService email,
                                        PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.accounts = accounts;
        this.email = email;
        this.encoder = encoder;
        this.clock = clock;
    }

    public record Delivery(UUID challengeId, Instant expiresAt, String email) {}
    private record Challenge(UUID id, String email, String hash, Instant requestedAt,
                             Instant expiresAt, int attempts) {}

    @Transactional
    public Delivery requestCode(UUID userId) {
        var user = users.findById(userId).filter(value -> value.isEnabled())
                .orElseThrow(() -> new BadRequestException("Compte administrateur indisponible"));
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, userId);
        Instant now = clock.instant();
        List<Challenge> existing = challenges(userId);
        if (!existing.isEmpty() && now.isBefore(existing.get(0).requestedAt().plusSeconds(60))) {
            throw new BadRequestException("Patientez une minute avant de demander un nouveau code");
        }
        var sender = accounts.findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                .findFirst().orElseThrow(() -> new BadRequestException(
                        "Connectez un compte Google dans Mon compte pour recevoir le code de reinitialisation"));
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        UUID challengeId = UUID.randomUUID();
        Instant expiresAt = now.plus(Duration.ofMinutes(10));
        jdbc.update("DELETE FROM application_reset_challenges WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO application_reset_challenges "
                        + "(user_id, challenge_id, email, code_hash, requested_at, expires_at, attempts) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0)", userId, challengeId, user.getEmail(), encoder.encode(code),
                Timestamp.from(now), Timestamp.from(expiresAt));
        email.sendAsUser(userId, sender, user.getEmail(), "inoDoc - Confirmation de reinitialisation",
                "Une reinitialisation complete de l'application a ete demandee depuis votre compte administrateur.\n\n"
                        + "Votre code de confirmation : " + code + "\n\n"
                        + "Ce code est valable 10 minutes et ne peut etre utilise qu'une fois. "
                        + "La reinitialisation supprime aussi les entretiens annuels et les carnets de carriere.\n\n"
                        + "Si vous n'etes pas a l'origine de cette demande, ne communiquez pas ce code "
                        + "et contactez votre administrateur.", null, null);
        return new Delivery(challengeId, expiresAt, user.getEmail());
    }

    @Transactional
    public boolean consume(UUID userId, UUID challengeId, String code) {
        List<Challenge> existing = challenges(userId);
        if (existing.isEmpty()) return false;
        Challenge challenge = existing.get(0);
        if (!challenge.id().equals(challengeId) || !clock.instant().isBefore(challenge.expiresAt())
                || challenge.attempts() >= 5) return false;
        jdbc.update("UPDATE application_reset_challenges SET attempts = attempts + 1 WHERE user_id = ?", userId);
        var user = users.findById(userId).filter(value -> value.isEnabled());
        if (user.isEmpty() || !challenge.email().equalsIgnoreCase(user.get().getEmail())
                || code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, challenge.hash())) return false;
        jdbc.update("DELETE FROM application_reset_challenges WHERE user_id = ?", userId);
        return true;
    }

    private List<Challenge> challenges(UUID userId) {
        return jdbc.query("SELECT challenge_id, email, code_hash, requested_at, expires_at, attempts "
                        + "FROM application_reset_challenges WHERE user_id = ? FOR UPDATE",
                (result, rowNumber) -> new Challenge(result.getObject("challenge_id", UUID.class),
                        result.getString("email"), result.getString("code_hash"),
                        result.getTimestamp("requested_at").toInstant(),
                        result.getTimestamp("expires_at").toInstant(), result.getInt("attempts")), userId);
    }
}