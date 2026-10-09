package com.esn.inodoc.repo;

import com.esn.inodoc.domain.TechSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechSkillRepository extends JpaRepository<TechSkill, UUID> {

    List<TechSkill> findAllByOrderByCategoryAscNameAsc();

    Optional<TechSkill> findFirstByNameIgnoreCase(String name);
}
