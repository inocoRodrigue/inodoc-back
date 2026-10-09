package com.esn.inodoc.repo;

import com.esn.inodoc.domain.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    Optional<CompanySettings> findFirstByOrderByCreatedAtAsc();
}
