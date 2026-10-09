package com.esn.inodoc.repo;

import com.esn.inodoc.domain.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory, UUID> {

    List<SkillCategory> findAllByOrderByName();
}
