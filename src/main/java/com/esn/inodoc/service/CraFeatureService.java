package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.dto.company.CraFeatureStatus;
import com.esn.inodoc.repo.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CraFeatureService {
    private final CompanySettingsRepository repository;

    public CraFeatureService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CraFeatureStatus status() {
        return new CraFeatureStatus(repository.findFirstByOrderByCreatedAtAsc()
                .map(CompanySettings::isCraEnabled)
                .orElse(false));
    }

    @Transactional
    public CraFeatureStatus setEnabled(boolean enabled) {
        CompanySettings settings = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(CompanySettings::new);
        settings.setCraEnabled(enabled);
        return new CraFeatureStatus(repository.save(settings).isCraEnabled());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return status().enabled();
    }
}