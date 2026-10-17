package com.esn.inodoc.controller;

import com.esn.inodoc.dto.company.CraFeatureStatus;
import com.esn.inodoc.service.CraFeatureService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cra-feature")
public class CraFeatureController {
    private final CraFeatureService service;

    public CraFeatureController(CraFeatureService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public CraFeatureStatus status() {
        return service.status();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CraFeatureStatus update(@RequestBody CraFeatureStatus request) {
        return service.setEnabled(request.enabled());
    }
}