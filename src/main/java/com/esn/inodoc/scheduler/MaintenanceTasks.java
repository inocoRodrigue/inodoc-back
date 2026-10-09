package com.esn.inodoc.scheduler;

import com.esn.inodoc.repo.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Taches de maintenance planifiees.
 */
@Component
public class MaintenanceTasks {

    private final RefreshTokenRepository refreshTokenRepository;

    public MaintenanceTasks(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Daily purge of expired refresh tokens. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }
}
