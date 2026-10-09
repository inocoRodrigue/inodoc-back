package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.repo.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Management of the ESN contact information, displayed in the header of
 * competence dossiers. Configurable by administrators and business
 * managers.
 */
@Service
public class CompanySettingsService {

    /** Signature applied by default to emails if none is configured. */
    public static final String DEFAULT_SIGNATURE = "Cordialement,\nL'équipe inoDoc";

    private final CompanySettingsRepository repository;

    public CompanySettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanyContactDto get() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(this::toDto)
                .orElseGet(() -> new CompanyContactDto(null, null, null, null, null));
    }

    /**
     * Effective signature to use in emails: the configured one if
     * present, otherwise the default signature.
     */
    @Transactional(readOnly = true)
    public String defaultEmailSignature() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(CompanySettings::getEmailSignature)
                .filter(s -> s != null && !s.isBlank())
                .orElse(DEFAULT_SIGNATURE);
    }

    @Transactional
    public CompanyContactDto update(CompanyContactDto request) {
        CompanySettings settings = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(CompanySettings::new);
        settings.setAddress(trimToNull(request.address()));
        settings.setPhone(trimToNull(request.phone()));
        settings.setEmail(trimToNull(request.email()));
        settings.setEmailSignature(trimToNull(request.emailSignature()));
        settings.setLogoUrl(trimToNull(request.logoUrl()));
        CompanySettings saved = repository.save(settings);
        return toDto(saved);
    }

    private CompanyContactDto toDto(CompanySettings settings) {
        return new CompanyContactDto(
                settings.getAddress(),
                settings.getPhone(),
                settings.getEmail(),
                settings.getEmailSignature(),
                settings.getLogoUrl());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
