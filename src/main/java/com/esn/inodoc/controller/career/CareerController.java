package com.esn.inodoc.controller.career;

import com.esn.inodoc.dto.career.CareerEntryDto;
import com.esn.inodoc.dto.career.CareerEntryRequest;
import com.esn.inodoc.dto.career.CareerTemplateDto;
import com.esn.inodoc.dto.career.CareerTemplateRequest;
import com.esn.inodoc.service.career.CareerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Career-log API: the shared template (managed by business managers) and the
 * authenticated user's personal entries. Ownership is enforced in the service.
 */
@RestController
@RequestMapping("/career")
@PreAuthorize("isAuthenticated()")
public class CareerController {

    private final CareerService service;

    public CareerController(CareerService service) {
        this.service = service;
    }

    @GetMapping("/template")
    public CareerTemplateDto getTemplate() {
        return service.getTemplate();
    }

    @PutMapping("/template")
    public CareerTemplateDto updateTemplate(@Valid @RequestBody CareerTemplateRequest request) {
        return service.updateTemplate(request);
    }

    @GetMapping("/entries")
    public List<CareerEntryDto> listMyEntries() {
        return service.listMyEntries();
    }

    @GetMapping("/entries/{id}")
    public CareerEntryDto getMyEntry(@PathVariable UUID id) {
        return service.getMyEntry(id);
    }

    @PostMapping("/entries")
    public ResponseEntity<CareerEntryDto> createMyEntry(@Valid @RequestBody CareerEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMyEntry(request));
    }

    @PutMapping("/entries/{id}")
    public CareerEntryDto updateMyEntry(@PathVariable UUID id, @Valid @RequestBody CareerEntryRequest request) {
        return service.updateMyEntry(id, request);
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteMyEntry(@PathVariable UUID id) {
        service.deleteMyEntry(id);
        return ResponseEntity.noContent().build();
    }
}
