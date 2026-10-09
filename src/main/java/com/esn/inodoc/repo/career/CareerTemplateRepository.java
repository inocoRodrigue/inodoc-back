package com.esn.inodoc.repo.career;

import com.esn.inodoc.domain.career.CareerTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CareerTemplateRepository extends JpaRepository<CareerTemplate, UUID> {
    Optional<CareerTemplate> findFirstByOrderByCreatedAtAsc();
}
