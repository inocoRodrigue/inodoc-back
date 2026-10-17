package com.esn.inodoc.controller;

import com.esn.inodoc.dto.dossier.TechSkillDto;
import com.esn.inodoc.service.TechSkillService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Catalog of technical skills offered as autocompletion.
 */
@RestController
@RequestMapping("/tech-skills")
public class TechSkillController {

    private final TechSkillService techSkillService;

    public TechSkillController(TechSkillService techSkillService) {
        this.techSkillService = techSkillService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public List<TechSkillDto> list() {
        return techSkillService.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
    public TechSkillDto create(@RequestBody TechSkillDto request) {
        return techSkillService.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        techSkillService.delete(id);
    }
}
