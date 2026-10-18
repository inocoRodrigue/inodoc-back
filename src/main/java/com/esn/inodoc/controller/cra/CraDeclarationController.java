package com.esn.inodoc.controller.cra;

import com.esn.inodoc.dto.cra.CraDeclarationDto;
import com.esn.inodoc.dto.cra.CraDeclarationSummaryDto;
import com.esn.inodoc.dto.cra.CraEntriesRequest;
import com.esn.inodoc.dto.cra.CraProjectDto;
import com.esn.inodoc.dto.cra.CraReviewPreviewDto;
import com.esn.inodoc.dto.cra.CraReviewUpdateRequest;
import com.esn.inodoc.dto.cra.CraValidatedFilter;
import com.esn.inodoc.dto.cra.CraValidatedFilterOptionsDto;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import com.esn.inodoc.service.cra.CraDeclarationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cra")
public class CraDeclarationController {
    private final CraDeclarationService service;

    public CraDeclarationController(CraDeclarationService service) {
        this.service = service;
    }

    @PostMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    @ResponseStatus(HttpStatus.CREATED)
    public CraDeclarationDto createCurrent() {
        return service.createCurrent();
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public CraDeclarationDto getCurrent() {
        return service.getCurrent();
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public List<CraDeclarationSummaryDto> listMine() {
        return service.listMine();
    }

    @GetMapping("/mine/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public CraDeclarationDto getMine(@PathVariable UUID id) {
        return service.getMine(id);
    }

    @PutMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public CraDeclarationDto saveEntries(@Valid @RequestBody CraEntriesRequest request) {
        return service.saveCurrentEntries(request);
    }

    @PostMapping("/current/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public CraDeclarationDto submit() {
        return service.submitCurrent();
    }

    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public List<CraDeclarationSummaryDto> listForReview() {
        return service.listForReview();
    }

    @GetMapping("/review/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraDeclarationDto getForReview(@PathVariable UUID id) {
        return service.getForReview(id);
    }

    @PostMapping("/review/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraDeclarationDto validate(@PathVariable UUID id) {
        return service.validate(id);
    }

    @GetMapping("/validated")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public Page<CraDeclarationDto> listValidated(@Valid @ModelAttribute CraValidatedFilter filter,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return service.listValidated(filter, page, size);
    }

    @GetMapping("/validated/filters")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraValidatedFilterOptionsDto validatedFilterOptions() {
        return service.validatedFilterOptions();
    }

    @PostMapping("/validated/{id}/unvalidate")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraDeclarationDto unvalidate(@PathVariable UUID id) {
        return service.unvalidate(id);
    }

    @GetMapping("/review/{id}/projects")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public List<CraProjectDto> projectsForReview(@PathVariable UUID id) {
        return service.projectsForReview(id);
    }

    @PostMapping("/review/{id}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraReviewPreviewDto previewReview(@PathVariable UUID id, @Valid @RequestBody CraEntriesRequest request) {
        return service.previewReviewChanges(id, request);
    }

    @PutMapping("/review/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraDeclarationDto saveReview(@PathVariable UUID id, @Valid @RequestBody CraReviewUpdateRequest request) {
        return service.saveReviewChanges(id, request);
    }
}