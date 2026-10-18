package com.esn.inodoc.service;

import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ApplicationResetConfirmationServiceTest.Config.class)
class ApplicationResetConfirmationServiceTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:reset-confirmation;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean MailAccountRepository accounts() { return mock(MailAccountRepository.class); }
        @Bean EmailService email() { return mock(EmailService.class); }
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean ApplicationResetConfirmationService confirmations(JdbcTemplate jdbc, UserRepository users,
                MailAccountRepository accounts, EmailService email, PasswordEncoder encoder, Clock clock) {
            return new ApplicationResetConfirmationService(jdbc, users, accounts, email, encoder, clock);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MailAccountRepository accounts;
    @Autowired EmailService email;
    @Autowired Clock clock;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationResetConfirmationService confirmations;
    private final UUID adminId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    private final AtomicReference<String> sentCode = new AtomicReference<>();
    private User admin;

    @BeforeEach
    void prepare() {
        reset(users, accounts, email, clock);
        when(clock.instant()).thenReturn(now);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V29__application_reset_challenges.sql"));
            return null;
        });
        jdbc.update("INSERT INTO users VALUES (?)", adminId);
        admin = new User();
        admin.setId(adminId);
        admin.setEmail("admin@example.test");
        admin.setEnabled(true);
        when(users.findById(adminId)).thenReturn(Optional.of(admin));
        MailAccount sender = new MailAccount();
        sender.setCreatedBy(adminId);
        sender.setActive(true);
        sender.setEmail("sender@example.test");
        when(accounts.findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(adminId)).thenReturn(List.of(sender));
        doAnswer(invocation -> {
            var match = Pattern.compile("confirmation : ([0-9]{6})").matcher(invocation.<String>getArgument(4));
            assertThat(match.find()).isTrue();
            sentCode.set(match.group(1));
            return null;
        }).when(email).sendAsUser(eq(adminId), eq(sender), eq(admin.getEmail()), anyString(), anyString(), isNull(), isNull());
    }

    @Test
    void codeIsEmailedHashedAndAcceptedOnlyOnce() {
        var delivery = confirmations.requestCode(adminId);
        assertThat(delivery.email()).isEqualTo(admin.getEmail());
        assertThat(delivery.expiresAt()).isEqualTo(now.plusSeconds(600));
        String hash = jdbc.queryForObject("SELECT code_hash FROM application_reset_challenges", String.class);
        assertThat(hash).isNotEqualTo(sentCode.get());
        assertThat(encoder.matches(sentCode.get(), hash)).isTrue();
        assertThat(confirmations.consume(adminId, delivery.challengeId(), sentCode.get())).isTrue();
        assertThat(confirmations.consume(adminId, delivery.challengeId(), sentCode.get())).isFalse();
    }

    @Test
    void fiveWrongAttemptsArePersistedAndBlockEvenTheCorrectCode() {
        var delivery = confirmations.requestCode(adminId);
        String wrong = sentCode.get().equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(confirmations.consume(adminId, delivery.challengeId(), wrong)).isFalse();
        }
        assertThat(jdbc.queryForObject("SELECT attempts FROM application_reset_challenges", Integer.class)).isEqualTo(5);
        assertThat(confirmations.consume(adminId, delivery.challengeId(), sentCode.get())).isFalse();
    }

    @Test
    void codeExpiresAtExactlyTenMinutes() {
        var delivery = confirmations.requestCode(adminId);
        when(clock.instant()).thenReturn(now.plusSeconds(600));
        assertThat(confirmations.consume(adminId, delivery.challengeId(), sentCode.get())).isFalse();
    }

    @Test
    void resendIsRateLimitedAndInvalidatesThePreviousChallenge() {
        var previous = confirmations.requestCode(adminId);
        String previousCode = sentCode.get();
        assertThatThrownBy(() -> confirmations.requestCode(adminId)).hasMessageContaining("une minute");
        when(clock.instant()).thenReturn(now.plusSeconds(61));
        var replacement = confirmations.requestCode(adminId);
        assertThat(confirmations.consume(adminId, previous.challengeId(), previousCode)).isFalse();
        assertThat(confirmations.consume(adminId, replacement.challengeId(), sentCode.get())).isTrue();
    }

    @Test
    void challengeCannotBeUsedByAnotherAdministratorOrAfterAnEmailChange() {
        var delivery = confirmations.requestCode(adminId);
        assertThat(confirmations.consume(UUID.randomUUID(), delivery.challengeId(), sentCode.get())).isFalse();
        admin.setEmail("changed@example.test");
        assertThat(confirmations.consume(adminId, delivery.challengeId(), sentCode.get())).isFalse();
    }

    @Test
    void unavailableSenderDoesNotCreateAnyChallenge() {
        when(accounts.findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(adminId)).thenReturn(List.of());
        assertThatThrownBy(() -> confirmations.requestCode(adminId)).hasMessageContaining("Connectez un compte Google");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_reset_challenges", Integer.class)).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void emailFailureRollsBackTheChallengeAndDoesNotStartTheCooldown() {
        doThrow(new IllegalStateException("Gmail unavailable")).when(email)
                .sendAsUser(any(), any(), anyString(), anyString(), anyString(), isNull(), isNull());
        assertThatThrownBy(() -> confirmations.requestCode(adminId)).hasMessage("Gmail unavailable");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_reset_challenges", Integer.class)).isZero();
    }

    @Test
    void concurrentVerificationAllowsOnlyOneConsumer() throws Exception {
        var delivery = confirmations.requestCode(adminId);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return confirmations.consume(adminId, delivery.challengeId(), sentCode.get()); });
            var second = executor.submit(() -> { start.await(); return confirmations.consume(adminId, delivery.challengeId(), sentCode.get()); });
            start.countDown();
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }
}