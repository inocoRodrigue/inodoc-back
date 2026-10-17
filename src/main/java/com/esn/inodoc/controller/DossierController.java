package com.esn.inodoc.controller;

import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.dossier.DossierSummaryDto;
import com.esn.inodoc.dto.dossier.DossierUpsertRequest;
import com.esn.inodoc.service.DossierService;
import com.esn.inodoc.service.MissionAdaptationService;
import com.esn.inodoc.dto.dossier.MissionAdaptationDto;
import com.esn.inodoc.dto.dossier.MissionAdaptationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Management of a person's competence dossier versions.
 */
@RestController
@RequestMapping("/profiles/{profileId}/dossiers")
public class DossierController {

    private final DossierService dossierService;
    private final MissionAdaptationService adaptationService;

    public DossierController(DossierService dossierService, MissionAdaptationService adaptationService) {
        this.dossierService = dossierService;
        this.adaptationService = adaptationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public List<DossierSummaryDto> list(@PathVariable UUID profileId) {
        return dossierService.listForProfile(profileId);
    }

    @GetMapping("/{dossierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public DossierDto get(@PathVariable UUID profileId, @PathVariable UUID dossierId) {
        return dossierService.get(dossierId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public ResponseEntity<DossierDto> create(@PathVariable UUID profileId,
                                             @Valid @RequestBody DossierUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dossierService.create(profileId, request));
    }

    @PutMapping("/{dossierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public DossierDto update(@PathVariable UUID profileId, @PathVariable UUID dossierId,
                             @Valid @RequestBody DossierUpsertRequest request) {
        return dossierService.update(dossierId, request);
    }

    @PostMapping("/{dossierId}/master")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public DossierDto setMaster(@PathVariable UUID profileId, @PathVariable UUID dossierId) {
        return dossierService.setMaster(dossierId);
    }

    @PostMapping("/{dossierId}/adapt-to-mission")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public MissionAdaptationDto adapt(@PathVariable UUID profileId, @PathVariable UUID dossierId,
                                      @Valid @RequestBody MissionAdaptationRequest request) {
        return adaptationService.adapt(profileId, dossierId, request);
    }

    @DeleteMapping("/{dossierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public ResponseEntity<Void> delete(@PathVariable UUID profileId, @PathVariable UUID dossierId) {
        dossierService.delete(dossierId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{dossierId}/dossier.pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID profileId, @PathVariable UUID dossierId) {
        DossierService.GeneratedDocument document = dossierService.generatePdf(dossierId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.filename()).build());
        return new ResponseEntity<>(document.content(), headers, HttpStatus.OK);
    }
}
