package com.esn.inodoc.repo;

import com.esn.inodoc.domain.CompetenceDossier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetenceDossierRepository extends JpaRepository<CompetenceDossier, UUID> {

    List<CompetenceDossier> findByProfileIdOrderByMasterDescCreatedAtAsc(UUID profileId);

    List<CompetenceDossier> findByProfileId(UUID profileId);
}
