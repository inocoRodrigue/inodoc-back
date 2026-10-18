package com.esn.inodoc.controller;

import com.esn.inodoc.service.ApplicationBackupService;
import com.esn.inodoc.service.ApplicationResetConfirmationService;
import com.esn.inodoc.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/admin/application-data")
@PreAuthorize("hasRole('ADMIN')")
public class ApplicationBackupController {
    private final ApplicationBackupService service;
    private final ApplicationResetConfirmationService confirmations;

    public ApplicationBackupController(ApplicationBackupService service, ApplicationResetConfirmationService confirmations) {
        this.service = service;
        this.confirmations = confirmations;
    }

    public record Confirmation(String confirmation, java.util.UUID challengeId, String code) {}

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return download(service.exportZip(), "sauvegarde");
    }

    @PostMapping(value = "/import", consumes = "application/zip")
    public ResponseEntity<Void> restore(HttpServletRequest request) throws java.io.IOException {
        int limit = 256 * 1024 * 1024;
        if (request.getContentLengthLong() > limit) throw new IllegalArgumentException("ZIP trop volumineux");
        byte[] zip = request.getInputStream().readNBytes(limit + 1);
        if (zip.length > limit) throw new IllegalArgumentException("ZIP trop volumineux");
        service.importZip(zip);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<byte[]> reset(@RequestBody Confirmation request) {
        if (!"REINITIALISER".equals(request.confirmation())) {
            throw new IllegalArgumentException("Confirmation de reinitialisation requise");
        }
        var userId = SecurityUtils.requirePrincipal().getUserId();
        if (!confirmations.consume(userId, request.challengeId(), request.code())) {
            throw new IllegalArgumentException("Code invalide, expire ou deja utilise. Demandez un nouveau code si necessaire.");
        }
        return download(service.reset(userId), "avant-reset");
    }

    @PostMapping("/reset/request-code")
    public ResponseEntity<ApplicationResetConfirmationService.Delivery> requestResetCode() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(confirmations.requestCode(SecurityUtils.requirePrincipal().getUserId()));
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String prefix) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inodoc-" + prefix + "-" + date + ".zip\"")
                .body(bytes);
    }
}