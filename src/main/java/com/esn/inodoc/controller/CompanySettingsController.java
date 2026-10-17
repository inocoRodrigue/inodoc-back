package com.esn.inodoc.controller;

import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.service.CompanySettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration of the ESN contact information.
 */
@RestController
@RequestMapping("/company-settings")
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;

    public CompanySettingsController(CompanySettingsService companySettingsService) {
        this.companySettingsService = companySettingsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public CompanyContactDto get() {
        return companySettingsService.get();
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
    public CompanyContactDto update(@RequestBody CompanyContactDto request) {
        return companySettingsService.update(request);
    }
}
