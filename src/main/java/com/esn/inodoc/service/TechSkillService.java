package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.TechSkill;
import com.esn.inodoc.dto.dossier.TechSkillDto;
import com.esn.inodoc.repo.TechSkillRepository;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Catalog of technical skills offered as autocompletion.
 * The catalog completes itself automatically with unknown entries.
 */
@Service
public class TechSkillService {

    private static final String DEFAULT_CATEGORY = "Autres";

    private final TechSkillRepository techSkillRepository;

    public TechSkillService(TechSkillRepository techSkillRepository) {
        this.techSkillRepository = techSkillRepository;
    }

    @Transactional(readOnly = true)
    public List<TechSkillDto> list() {
        return techSkillRepository.findAllByOrderByCategoryAscNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** Manually adds a skill to the catalog (case-insensitive). */
    @Transactional
    public TechSkillDto create(TechSkillDto request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Le nom de la compétence est obligatoire");
        }
        String category = hasText(request.category()) ? request.category().trim() : DEFAULT_CATEGORY;
        TechSkill existing = techSkillRepository.findFirstByNameIgnoreCase(name).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }
        TechSkill skill = new TechSkill();
        skill.setCategory(category);
        skill.setName(name);
        TechSkill saved = techSkillRepository.save(skill);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        TechSkill skill = techSkillRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Compétence introuvable"));
        techSkillRepository.delete(skill);
    }

    /** Adds to the catalog all dossier skills still unknown (case-insensitive). */
    @Transactional
    public void harvest(CompetenceDossier dossier) {
        for (SkillGroup group : dossier.getSkillGroups()) {
            String category = hasText(group.getCategory()) ? group.getCategory().trim() : DEFAULT_CATEGORY;
            for (String token : splitTokens(group.getContent())) {
                addIfMissing(category, token);
            }
        }
        dossier.getExperiences().forEach(exp -> exp.getProjects().forEach(project -> {
            for (String token : splitTokens(project.getTechnologies())) {
                addIfMissing("Technologies", token);
            }
        }));
    }

    private void addIfMissing(String category, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (techSkillRepository.findFirstByNameIgnoreCase(trimmed).isPresent()) {
            return;
        }
        TechSkill skill = new TechSkill();
        skill.setCategory(category);
        skill.setName(trimmed);
        techSkillRepository.save(skill);
    }

    private List<String> splitTokens(String content) {
        if (!hasText(content)) {
            return List.of();
        }
        return List.of(content.split("[,;\\n]"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TechSkillDto toDto(TechSkill skill) {
        return new TechSkillDto(skill.getId(), skill.getCategory(), skill.getName());
    }
}
