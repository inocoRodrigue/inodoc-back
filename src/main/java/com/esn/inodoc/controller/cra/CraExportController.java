package com.esn.inodoc.controller.cra;
import com.esn.inodoc.service.cra.*;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.*;

@RestController
@RequestMapping("/cra")
public class CraExportController {
    private final CraExportService exports;
    private final CraDeclarationService declarations;
    private final CraRulesService rules;
    private final CraAuditService audit;
    public CraExportController(CraExportService exports, CraDeclarationService declarations, CraRulesService rules, CraAuditService audit) {
        this.exports = exports; this.declarations = declarations; this.rules = rules; this.audit = audit;
    }
    @GetMapping("/exports/{year}/{month}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER')")
    public ResponseEntity<StreamingResponseBody> period(@PathVariable int year, @PathVariable int month,
            @RequestParam(defaultValue = "csv") String format, @RequestParam(defaultValue = "NONE") String group) {
        rules.period(year, month); String mime = exports.mime(format);
        if (!Set.of("NONE", "PROJECT", "CLIENT", "COLLABORATOR").contains(group)) throw new com.esn.inodoc.controller.error.BadRequestException("Regroupement invalide");
        audit.recordRaw(null, "EXPORT_REQUESTED", year + "-" + month + " / " + format + " / " + group, null, null);
        return response(mime, "CRA-" + year + "-" + month + "." + format, output -> exports.writePeriod(year, month, format, group, output));
    }
    @GetMapping("/mine/{id}/export")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_MANAGER','BUSINESS_MANAGER','INTERNAL')")
    public ResponseEntity<StreamingResponseBody> own(@PathVariable UUID id, @RequestParam(defaultValue = "pdf") String format) {
        String mime = exports.mime(format); var declaration = declarations.getMine(id);
        boolean includeDailyRate = SecurityUtils.requirePrincipal().getAuthorityNames().stream()
            .anyMatch(role -> Set.of("ROLE_ADMIN", "ROLE_FINANCE_MANAGER", "ROLE_BUSINESS_MANAGER").contains(role));
        audit.recordRaw(id, "EXPORT_REQUESTED", format, null, null);
        return response(mime, "CRA-" + declaration.year() + "-" + declaration.month() + "." + format,
            output -> exports.writeOwn(declaration, format, output, includeDailyRate));
    }
    private ResponseEntity<StreamingResponseBody> response(String mime, String filename, StreamingResponseBody body) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore()).body(body);
    }
}