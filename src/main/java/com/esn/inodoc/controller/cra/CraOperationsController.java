package com.esn.inodoc.controller.cra;

import com.esn.inodoc.domain.cra.CraReminder;
import com.esn.inodoc.dto.cra.*;
import com.esn.inodoc.service.cra.*;
import com.esn.inodoc.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/cra")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER')")
public class CraOperationsController {
    private final CraRulesService rules;
    private final CraAuditService audit;
    private final CraDeclarationService declarations;
    private final CraReportingService reporting;
    public CraOperationsController(CraRulesService rules, CraAuditService audit, CraDeclarationService declarations, CraReportingService reporting) {
        this.rules = rules; this.audit = audit; this.declarations = declarations; this.reporting = reporting;
    }
    @GetMapping("/policy")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public CraOperationsDto.Policy policy() { return rules.policyDto(); }
    @PutMapping("/policy") @PreAuthorize("hasRole('ADMIN')")
    public CraOperationsDto.Policy savePolicy(@Valid @RequestBody CraOperationsDto.Policy body) { return rules.savePolicy(body); }
    @GetMapping("/calendar/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public CraOperationsDto.Calendar calendar(@PathVariable int year, @PathVariable int month, @RequestParam(required = false) UUID userId) {
        var principal = SecurityUtils.requirePrincipal();
        UUID target = userId == null ? principal.getUserId() : userId;
        boolean reviewer = principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN") || role.getAuthority().equals("ROLE_FINANCE_MANAGER"));
        if (!target.equals(principal.getUserId()) && !reviewer) throw new org.springframework.security.access.AccessDeniedException("Calendrier non autorisé");
        return rules.calendar(target, rules.period(year, month));
    }
    @PostMapping("/mine/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public CraDeclarationDto create(@PathVariable int year, @PathVariable int month) { return declarations.create(year, month); }
    @PutMapping("/mine/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public CraDeclarationDto save(@PathVariable UUID id, @Valid @RequestBody CraEntriesRequest body) { return declarations.saveEntries(id, body); }
    @PostMapping("/mine/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public CraDeclarationDto submit(@PathVariable UUID id) { return declarations.submit(id); }
    @PostMapping("/review/{id}/reject")
    public CraDeclarationDto reject(@PathVariable UUID id, @Valid @RequestBody CraOperationsDto.Reason body) { return declarations.reject(id, body.reason()); }
    @GetMapping("/history/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public Page<CraOperationsDto.History> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return audit.history(id, page, size);
    }
    @GetMapping("/monitor/{year}/{month}")
    public Page<CraOperationsDto.Monitor> monitor(@PathVariable int year, @PathVariable int month,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return reporting.monitor(year, month, status, search, page, size);
    }
    @GetMapping("/statistics/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER')")
    public CraOperationsDto.Statistics statistics(@PathVariable int year, @PathVariable int month) {
        return reporting.statistics(year, month);
    }
    @PostMapping("/monitor/{year}/{month}/remind")
    public int remind(@PathVariable int year, @PathVariable int month, @Valid @RequestBody CraOperationsDto.ReminderRequest body) { return reporting.queue(year, month, body); }
    @GetMapping("/reminders")
    public Page<CraReminder> reminders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) { return reporting.reminders(page, size); }
}