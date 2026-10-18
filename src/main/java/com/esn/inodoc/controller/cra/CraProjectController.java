package com.esn.inodoc.controller.cra;

import com.esn.inodoc.dto.cra.CraCollaboratorDto;
import com.esn.inodoc.dto.cra.CraProjectDto;
import com.esn.inodoc.dto.cra.CraProjectRequest;
import com.esn.inodoc.service.cra.CraProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/cra-projects")
public class CraProjectController {
    private final CraProjectService service;

    public CraProjectController(CraProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public List<CraProjectDto> listForManagement() {
        return service.listForManagement();
    }

    @GetMapping("/collaborators")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public List<CraCollaboratorDto> listCollaborators() {
        return service.listCollaborators();
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public List<CraProjectDto> availableForCurrentCollaborator() {
        return service.availableForCurrentCollaborator();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CraProjectDto create(@Valid @RequestBody CraProjectRequest request) {
        return service.createProject(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    public CraProjectDto update(@PathVariable UUID id, @Valid @RequestBody CraProjectRequest request) {
        return service.updateProject(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deleteCustomProject(id);
    }
}