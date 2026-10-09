package com.esn.inodoc.scheduler;

import com.esn.inodoc.repo.CompetenceDossierRepository;
import com.esn.inodoc.repo.RefreshTokenRepository;
import com.esn.inodoc.service.CompanySettingsService;
import com.esn.inodoc.service.DossierObsolescence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Taches de maintenance planifiees.
 */
@Component
public class MaintenanceTasks {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final CompetenceDossierRepository dossierRepository;
    private final CompanySettingsService companySettingsService;
    private YearMonth lastObsolescenceCheck;

    public MaintenanceTasks(RefreshTokenRepository refreshTokenRepository,
                            CompetenceDossierRepository dossierRepository,
                            CompanySettingsService companySettingsService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.dossierRepository = dossierRepository;
        this.companySettingsService = companySettingsService;
    }

    /** Daily purge of expired refresh tokens. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    /** Audit master dossiers at the configured month interval. */
    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional(readOnly = true)
    public void checkObsoleteMasterDossiers() {
        int intervalMonths = companySettingsService.get().staleDossierMonths();
        YearMonth currentMonth = YearMonth.now();
        if (lastObsolescenceCheck != null
            && ChronoUnit.MONTHS.between(lastObsolescenceCheck, currentMonth) < intervalMonths) {
            return;
        }

        long obsoleteCount = dossierRepository.findByMasterTrue().stream()
                .filter(dossier -> DossierObsolescence.isObsolete(
                        true, dossier.getUpdatedAt(), intervalMonths))
                .count();
        log.info("Master dossier obsolescence check complete: {} dossier(s) need an update (threshold: {} months)",
                obsoleteCount, intervalMonths);
        lastObsolescenceCheck = currentMonth;
    }
}
