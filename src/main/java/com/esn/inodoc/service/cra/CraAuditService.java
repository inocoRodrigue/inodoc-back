package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.dto.cra.CraOperationsDto;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.*;
import com.esn.inodoc.utils.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CraAuditService {
    private final CraAuditRepository events;
    private final CraDeclarationRepository declarations;
    private final UserRepository users;
    private final ObjectMapper mapper;
    public CraAuditService(CraAuditRepository events, CraDeclarationRepository declarations, UserRepository users, ObjectMapper mapper) {
        this.events = events; this.declarations = declarations; this.users = users; this.mapper = mapper;
    }
    public String snapshot(CraDeclaration declaration) {
        var state = new LinkedHashMap<String, Object>();
        state.put("status", declaration.getStatus());
        state.put("year", declaration.getYear()); state.put("month", declaration.getMonth());
        state.put("declaredAt", declaration.getDeclaredAt()); state.put("validatedAt", declaration.getValidatedAt());
        state.put("validatedBy", declaration.getValidatedBy() == null ? null : declaration.getValidatedBy().getId());
        state.put("rejectionReason", declaration.getRejectionReason());
        state.put("activities", declaration.getEntries().stream().map(day -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("date", day.getWorkDate()); value.put("projectId", day.getProject().getId());
            value.put("projectName", day.getProject().getName()); value.put("workLocation", day.getWorkLocation());
            value.put("dayPart", day.getDayPart());
            value.put("afternoonProjectId", day.getAfternoonProject() == null ? null : day.getAfternoonProject().getId());
            value.put("afternoonWorkLocation", day.getAfternoonWorkLocation());
            return value;
        }).toList());
        return json(state);
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("CRA audit serialization failed", error); }
    }
    @Transactional
    public void record(CraDeclaration declaration, String action, String reason, String before) {
        recordRaw(declaration == null ? null : declaration.getId(), action, reason, before,
                declaration == null ? null : snapshot(declaration));
    }
    @Transactional
    public void recordRaw(UUID declarationId, String action, String reason, String before, String after) {
        var principal = SecurityUtils.requirePrincipal();
        var actor = users.findById(principal.getUserId()).orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        CraAuditEvent event = new CraAuditEvent();
        event.setDeclarationId(declarationId); event.setActorId(actor.getId());
        event.setActorName(actor.getFirstName() + " " + actor.getLastName());
        event.setAction(action); event.setReason(reason); event.setBeforeState(before); event.setAfterState(after);
        events.save(event);
    }
    @Transactional(readOnly = true)
    public Page<CraOperationsDto.History> history(UUID id, int page, int size) {
        var principal = SecurityUtils.requirePrincipal();
        boolean reviewer = principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN") || role.getAuthority().equals("ROLE_FINANCE_MANAGER"));
        var declaration = declarations.findById(id).orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
        if (!reviewer && !declaration.getCollaborator().getId().equals(principal.getUserId())) throw new NotFoundException("Déclaration introuvable");
        if (page < 0 || size < 1 || size > 100) throw new com.esn.inodoc.controller.error.BadRequestException("Pagination invalide");
        return events.findByDeclarationIdOrderByCreatedAtDesc(id, PageRequest.of(page, size)).map(event ->
                new CraOperationsDto.History(event.getId(), event.getCreatedAt(), event.getActorName(), event.getAction(),
                        event.getReason(), event.getBeforeState(), event.getAfterState()));
    }
}