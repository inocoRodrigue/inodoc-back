package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.*;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.dto.cra.CraOperationsDto;
import com.esn.inodoc.dto.cra.CraDayAssignmentRequest;
import com.esn.inodoc.repo.cra.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class CraRulesService {
    private final CraPolicyRepository policies;
    private final CraAuditService audit;
    private final CraProjectRepository projects;
    public CraRulesService(CraPolicyRepository policies, CraAuditService audit, CraProjectRepository projects) {
        this.policies = policies; this.audit = audit; this.projects = projects;
    }
    public YearMonth period(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) throw new BadRequestException("Période invalide");
        return YearMonth.of(year, month);
    }
    @Transactional(readOnly = true)
    public CraPolicy policy() { return policies.findById(1).orElseGet(CraPolicy::new); }
    public CraOperationsDto.Policy policyDto() {
        return policyDto(policy());
    }
    private CraOperationsDto.Policy policyDto(CraPolicy policy) {
        return new CraOperationsDto.Policy(policy.isAllowSelfApproval(), Set.copyOf(policy.getHolidays()),
                policy.getDateRules().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
                    var rule = entry.getValue(); var project = rule.getProject();
                    return new CraOperationsDto.DateRule(entry.getKey(), rule.getType(), rule.getLabel(),
                            project == null ? null : project.getId(), project == null ? null : project.getName());
                }).toList());
    }
    public CraOperationsDto.Policy savePolicy(CraOperationsDto.Policy request) {
        if (request.holidays() == null) throw new BadRequestException("Règles invalides");
        var before = audit.json(policyDto()); var policy = policy();
        var holidays = new HashSet<>(request.holidays());
        if (request.dateRules() != null) {
            var dated = new HashMap<LocalDate, CraDateRule>();
            for (var input : request.dateRules()) {
                if (input == null || input.date() == null || input.type() == null || input.date().getYear() < 2000 || input.date().getYear() > 2100)
                    throw new BadRequestException("Date ou type de règle invalide");
                if (dated.containsKey(input.date())) throw new BadRequestException("Une seule règle est autorisée par date");
                var rule = new CraDateRule(); rule.setType(input.type());
                if (input.label() != null && input.label().length() > 255) throw new BadRequestException("Libellé trop long");
                rule.setLabel(input.label() == null || input.label().isBlank() ? null : input.label().trim());
                if (input.type() == CraDateRuleType.ASSIGNED_ACTIVITY) {
                    var previous = policy.getDateRules().get(input.date());
                    var project = projects.findById(input.projectId() == null ? new UUID(0, 0) : input.projectId())
                        .filter(value -> value.getType() == CraProjectType.ACCESS_ALL && (!value.isArchived()
                            || (previous != null && previous.getType() == CraDateRuleType.ASSIGNED_ACTIVITY
                            && previous.getProject() != null && previous.getProject().getId().equals(value.getId()))))
                            .orElseThrow(() -> new BadRequestException("Une activité partagée active est obligatoire pour une journée imposée"));
                    rule.setProject(project); holidays.remove(input.date());
                } else {
                    if (input.projectId() != null) throw new BadRequestException("Un jour non travaillé ne peut pas avoir de projet");
                    holidays.add(input.date());
                }
                dated.put(input.date(), rule);
            }
            policy.setDateRules(dated);
        }
        policy.setAllowSelfApproval(request.allowSelfApproval()); policy.setHolidays(holidays);
        policies.save(policy);
        audit.recordRaw(null, "POLICY_UPDATED", null, before, audit.json(policyDto(policy)));
        return policyDto(policy);
    }
    public CraOperationsDto.Calendar calendar(UUID userId, YearMonth month) {
        var policy = policy();
        var days = new ArrayList<CraOperationsDto.CalendarDay>();
        double expected = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
                var date = month.atDay(day);
                var rule = policy.getDateRules().get(date);
                boolean imposed = rule != null && rule.getType() == CraDateRuleType.ASSIGNED_ACTIVITY;
                boolean holiday = rule != null ? !imposed : policy.getHolidays().contains(date);
                boolean working = imposed || (rule == null && date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue() && !holiday);
                var project = imposed ? rule.getProject() : null;
                String label = rule != null && rule.getLabel() != null ? rule.getLabel()
                    : imposed ? project.getName() : rule != null && rule.getType() == CraDateRuleType.NON_WORKING ? "Jour non travaillé"
                    : holiday ? "Jour férié" : working ? null : "Week-end";
                days.add(new CraOperationsDto.CalendarDay(date, working, working, holiday, rule != null || !working,
                    rule == null ? holiday ? CraDateRuleType.PUBLIC_HOLIDAY : null : rule.getType(), label,
                    project == null ? null : project.getId(), project == null ? null : project.getName()));
                if (working) expected++;
        }
        return new CraOperationsDto.Calendar(days, expected);
    }
    public Map<LocalDate, CraProject> imposedProjects(CraDeclaration declaration) {
        var period = YearMonth.of(declaration.getYear(), declaration.getMonth());
        var result = new HashMap<LocalDate, CraProject>();
        policy().getDateRules().forEach((date, rule) -> {
            if (YearMonth.from(date).equals(period) && rule.getType() == CraDateRuleType.ASSIGNED_ACTIVITY) result.put(date, rule.getProject());
        });
        return result;
    }
    @Transactional(readOnly = true)
    public List<CraDayEntry> effectiveEntries(CraDeclaration declaration) {
        if (declaration.getStatus() == CraDeclarationStatus.VALIDATED) return List.copyOf(declaration.getEntries());
        var entries = new TreeMap<LocalDate, CraDayEntry>();
        declaration.getEntries().forEach(entry -> entries.put(entry.getWorkDate(), entry));
        var imposed = imposedProjects(declaration);
        for (var day : calendar(declaration.getCollaborator().getId(), YearMonth.of(declaration.getYear(), declaration.getMonth())).days()) {
            if (!day.morning() && !day.afternoon()) entries.remove(day.date());
            else if (imposed.containsKey(day.date())) {
                var entry = new CraDayEntry(); entry.setDeclaration(declaration); entry.setWorkDate(day.date());
                entry.setProject(imposed.get(day.date())); entry.setDayPart(CraDayPart.FULL_DAY);
                entries.put(day.date(), entry);
            }
        }
        return List.copyOf(entries.values());
    }
    public List<CraDayAssignmentRequest> normalizeAssignments(CraDeclaration declaration, List<CraDayAssignmentRequest> request) {
        if (request == null) throw new BadRequestException("Les activités sont obligatoires");
        var days = calendar(declaration.getCollaborator().getId(), YearMonth.of(declaration.getYear(), declaration.getMonth())).days()
                .stream().collect(java.util.stream.Collectors.toMap(CraOperationsDto.CalendarDay::date, java.util.function.Function.identity()));
        var result = new TreeMap<LocalDate, CraDayAssignmentRequest>();
        for (var input : request) {
            if (input == null || input.date() == null) throw new BadRequestException("Date d'activité invalide");
            if (result.putIfAbsent(input.date(), input) != null) throw new BadRequestException("Un seul projet peut être choisi par jour");
            var day = days.get(input.date());
            if (day == null) throw new BadRequestException("L'activité doit appartenir au mois de la déclaration");
            if (!day.morning() && !day.afternoon()) throw new BadRequestException("La journée du " + day.date() + " est non travaillée");
            if (day.ruleType() == CraDateRuleType.ASSIGNED_ACTIVITY && (!Objects.equals(input.projectId(), day.projectId())
                    || input.dayPart() != CraDayPart.FULL_DAY || input.workLocation() != null
                    || input.afternoonProjectId() != null || input.afternoonWorkLocation() != null)) {
                throw new BadRequestException("La journée du " + day.date() + " est imposée par les règles de déclaration et ne peut pas être modifiée");
            }
        }
        for (var day : days.values()) {
            if (day.ruleType() == CraDateRuleType.ASSIGNED_ACTIVITY)
                result.put(day.date(), new CraDayAssignmentRequest(day.date(), day.projectId(), null));
        }
        return List.copyOf(result.values());
    }
    public void synchronize(CraDeclaration declaration) {
        var effective = effectiveEntries(declaration);
        var existing = declaration.getEntries().stream().collect(java.util.stream.Collectors.toMap(CraDayEntry::getWorkDate, java.util.function.Function.identity()));
        var retained = effective.stream().map(CraDayEntry::getWorkDate).collect(java.util.stream.Collectors.toSet());
        declaration.getEntries().removeIf(entry -> !retained.contains(entry.getWorkDate()));
        for (var entry : effective) {
            var saved = existing.get(entry.getWorkDate());
            if (saved == entry) continue;
            if (saved == null) declaration.getEntries().add(entry);
            else {
                saved.setProject(entry.getProject()); saved.setDayPart(entry.getDayPart()); saved.setWorkLocation(entry.getWorkLocation());
                saved.setAfternoonProject(entry.getAfternoonProject()); saved.setAfternoonWorkLocation(entry.getAfternoonWorkLocation());
            }
        }
    }
    public void requireComplete(CraDeclaration declaration) {
        var entries = new HashMap<LocalDate, CraDayEntry>();
        effectiveEntries(declaration).forEach(day -> entries.put(day.getWorkDate(), day));
        for (var day : calendar(declaration.getCollaborator().getId(), YearMonth.of(declaration.getYear(), declaration.getMonth())).days()) {
            var entry = entries.get(day.date());
            boolean morning = entry != null && entry.getDayPart() != CraDayPart.AFTERNOON;
            boolean afternoon = entry != null && entry.getDayPart() != CraDayPart.MORNING;
            if ((day.morning() && !morning) || (day.afternoon() && !afternoon)) throw new BadRequestException("Toutes les demi-journées travaillées du mois doivent être renseignées, hors jours fériés et fermetures");
        }
    }
}