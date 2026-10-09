package com.esn.inodoc.repo.career;

import com.esn.inodoc.domain.career.CareerEntryShare;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerEntryShareRepository extends JpaRepository<CareerEntryShare, UUID> {
    @EntityGraph(attributePaths = {"careerEntry", "businessManager"})
    List<CareerEntryShare> findAllByBusinessManagerIdOrderByCreatedAtDesc(UUID businessManagerId);

    @EntityGraph(attributePaths = "businessManager")
    List<CareerEntryShare> findAllByCareerEntryIdOrderByCreatedAtAsc(UUID careerEntryId);

    Optional<CareerEntryShare> findByCareerEntryIdAndBusinessManagerId(UUID careerEntryId, UUID businessManagerId);

    boolean existsByCareerEntryIdAndBusinessManagerId(UUID careerEntryId, UUID businessManagerId);
}