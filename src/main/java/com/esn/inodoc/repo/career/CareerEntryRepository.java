package com.esn.inodoc.repo.career;

import com.esn.inodoc.domain.career.CareerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerEntryRepository extends JpaRepository<CareerEntry, UUID> {
    List<CareerEntry> findByUserIdOrderByEntryDateDescCreatedAtDesc(UUID userId);

    Optional<CareerEntry> findByIdAndUserId(UUID id, UUID userId);
}
