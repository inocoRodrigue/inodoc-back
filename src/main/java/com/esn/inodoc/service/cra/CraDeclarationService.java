package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraDayEntry;
import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.dto.cra.CraDayAssignmentRequest;
import com.esn.inodoc.dto.cra.CraDeclarationDto;
import com.esn.inodoc.dto.cra.CraDeclarationSummaryDto;
import com.esn.inodoc.dto.cra.CraEntriesRequest;
import com.esn.inodoc.dto.cra.CraEntryDto;
import com.esn.inodoc.dto.cra.CraProjectDto;
import com.esn.inodoc.dto.cra.CraReviewPreviewDto;
import com.esn.inodoc.dto.cra.CraReviewUpdateRequest;
import com.esn.inodoc.dto.cra.CraValidatedFilter;
import com.esn.inodoc.dto.cra.CraValidatedFilterOptionsDto;
import com.esn.inodoc.repo.cra.CraDeclarationSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.CraDeclarationRepository;
import com.esn.inodoc.repo.cra.CraProjectRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.service.CraFeatureService;
import com.esn.inodoc.service.EmailService;
import com.esn.inodoc.service.MailAccountService;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CraDeclarationService {
    private final CraDeclarationRepository declarationRepository;
    private final CraProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CraFeatureService craFeatureService;
    private final MailAccountService mailAccountService;
    private final EmailService emailService;
    private final CraRulesService rules;
    private final CraAuditService audit;

    public CraDeclarationService(CraDeclarationRepository declarationRepository,
                                 CraProjectRepository projectRepository,
                                 UserRepository userRepository,
                                 CraFeatureService craFeatureService,
                                 MailAccountService mailAccountService,
                                 EmailService emailService, CraRulesService rules, CraAuditService audit) {
        this.declarationRepository = declarationRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.craFeatureService = craFeatureService;
        this.mailAccountService = mailAccountService;
        this.emailService = emailService;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional
    public CraDeclarationDto createCurrent() {
        YearMonth period = YearMonth.now();
        return create(period.getYear(), period.getMonthValue());
    }

    @Transactional
    public CraDeclarationDto create(int year, int month) {
        YearMonth period = rules.period(year, month);
        if (!period.equals(YearMonth.now())) throw new BadRequestException("Un CRA peut être créé uniquement pour le mois en cours");
        UUID userId = currentUserId();
        if (declarationRepository.findByCollaborator_IdAndYearAndMonth(userId, period.getYear(), period.getMonthValue())
                .isPresent()) {
            throw new ConflictException("Une déclaration existe déjà pour ce mois");
        }
        User collaborator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        CraDeclaration declaration = new CraDeclaration();
        declaration.setCollaborator(collaborator);
        declaration.setYear(period.getYear());
        declaration.setMonth(period.getMonthValue());
        declaration.setStatus(CraDeclarationStatus.PENDING);
        rules.synchronize(declaration);
        declarationRepository.save(declaration);
        audit.record(declaration, "CREATED", null, null);
        return toDto(declaration);
    }

    @Transactional(readOnly = true)
    public CraDeclarationDto getCurrent() {
        return toDto(requireCurrent());
    }

    @Transactional(readOnly = true)
    public List<CraDeclarationSummaryDto> listMine() {
        return declarationRepository.findByCollaborator_IdOrderByYearDescMonthDesc(currentUserId()).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CraDeclarationDto getMine(UUID id) {
        return declarationRepository.findByIdAndCollaborator_Id(id, currentUserId())
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
    }

    @Transactional
    public CraDeclarationDto saveCurrentEntries(CraEntriesRequest request) {
        return saveDraft(requireEditableCurrent(), request);
    }

    @Transactional
    public CraDeclarationDto saveEntries(UUID id, CraEntriesRequest request) {
        return saveDraft(requireOwned(id), request);
    }

    private CraDeclarationDto saveDraft(CraDeclaration declaration, CraEntriesRequest request) {
        if (declaration.getStatus() != CraDeclarationStatus.PENDING) throw new ConflictException("Seul un brouillon peut être modifié");
        String before = audit.snapshot(declaration);
        replaceEntries(declaration, request.days());
        declarationRepository.save(declaration);
        audit.record(declaration, "UPDATED", null, before);
        return toDto(declaration);
    }

    @Transactional
    public CraDeclarationDto submitCurrent() {
        return submit(requireEditableCurrent());
    }

    @Transactional
    public CraDeclarationDto submit(UUID id) { return submit(requireOwned(id)); }

    private CraDeclarationDto submit(CraDeclaration declaration) {
        if (declaration.getStatus() != CraDeclarationStatus.PENDING) throw new ConflictException("Seul un brouillon peut être déclaré");
        rules.requireComplete(declaration);
        String before = audit.snapshot(declaration);
        rules.synchronize(declaration);
        declaration.setStatus(CraDeclarationStatus.DECLARED);
        declaration.setDeclaredAt(Instant.now());
        declaration.setRejectionReason(null);
        declarationRepository.save(declaration);
        audit.record(declaration, "SUBMITTED", null, before);
        return toDto(declaration);
    }

    private CraDeclaration requireOwned(UUID id) {
        return declarationRepository.findByIdAndCollaborator_Id(id, currentUserId())
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
    }

    @Transactional(readOnly = true)
    public List<CraDeclarationSummaryDto> listForReview() {
        return declarationRepository.findForReview(CraDeclarationStatus.DECLARED).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CraDeclarationDto getForReview(UUID id) {
        CraDeclaration declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
        return toDto(declaration);
    }

    @Transactional(readOnly = true)
    public List<CraProjectDto> projectsForReview(UUID id) {
        CraDeclaration declaration = requireDeclared(id);
        Map<UUID, CraProject> projects = projectRepository.findAvailableForUser(
                declaration.getCollaborator().getId(), CraProjectType.CLIENT).stream()
                .collect(Collectors.toMap(CraProject::getId, Function.identity()));
        var effective = rules.effectiveEntries(declaration);
        effective.forEach(entry -> projects.putIfAbsent(entry.getProject().getId(), entry.getProject()));
        effective.stream().filter(entry -> entry.getAfternoonProject() != null)
            .forEach(entry -> projects.putIfAbsent(entry.getAfternoonProject().getId(), entry.getAfternoonProject()));
        return projects.values().stream()
                .sorted(java.util.Comparator.comparing(CraProject::getName))
                .map(project -> new CraProjectDto(project.getId(), project.getCode(), project.getName(),
                        project.getDescription(), project.getType(), project.isSystemDefined(), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CraReviewPreviewDto previewReviewChanges(UUID id, CraEntriesRequest request) {
        CraDeclaration declaration = requireDeclared(id);
        var days = rules.normalizeAssignments(declaration, request.days());
        Map<LocalDate, CraProject> proposed = resolveEntries(declaration, days);
        return reviewPreview(declaration, days, proposed);
    }

    @Transactional
    public CraDeclarationDto saveReviewChanges(UUID id, CraReviewUpdateRequest request) {
        CraDeclaration declaration = requireDeclared(id);
        String before = audit.snapshot(declaration);
        var days = rules.normalizeAssignments(declaration, request.days());
        Map<LocalDate, CraProject> proposed = resolveEntries(declaration, days);
        CraReviewPreviewDto preview = reviewPreview(declaration, days, proposed);
        if (preview.changedDays() == 0) {
            throw new BadRequestException("Aucune modification à enregistrer");
        }
        if (request.mailAccountId() != null) {
            var account = mailAccountService.requireActive(request.mailAccountId());
            emailService.send(account, preview.recipient(), preview.subject(), preview.body(), null, null);
        }
        applyEntries(declaration, days, proposed);
        declarationRepository.save(declaration);
        audit.record(declaration, "CORRECTED", request.mailAccountId() == null ? "Sans e-mail" : "E-mail demandé", before);
        return toDto(declaration);
    }

    private CraDeclaration requireDeclared(UUID id) {
        CraDeclaration declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
        if (declaration.getStatus() != CraDeclarationStatus.DECLARED) {
            throw new ConflictException("Seule une déclaration envoyée peut être corrigée");
        }
        return declaration;
    }

    private CraReviewPreviewDto reviewPreview(CraDeclaration declaration, List<CraDayAssignmentRequest> days,
                                               Map<LocalDate, CraProject> proposedProjects) {
        Map<LocalDate, CraDayEntry> previous = declaration.getEntries().stream()
                .collect(Collectors.toMap(CraDayEntry::getWorkDate, Function.identity()));
        Map<LocalDate, CraDayAssignmentRequest> proposed = days.stream()
                .collect(Collectors.toMap(CraDayAssignmentRequest::date, Function.identity()));
        Set<LocalDate> dates = new TreeSet<>(previous.keySet());
        dates.addAll(proposed.keySet());
        List<String> changes = new ArrayList<>();
        for (LocalDate date : dates) {
            CraDayEntry before = previous.get(date);
            CraDayAssignmentRequest after = proposed.get(date);
            if (before != null && after != null
                    && before.getProject().getId().equals(after.projectId())
                    && Objects.equals(before.getWorkLocation(), after.workLocation())
                    && before.getDayPart() == after.dayPart()
                    && Objects.equals(before.getAfternoonProject() == null ? null : before.getAfternoonProject().getId(), after.afternoonProjectId())
                    && Objects.equals(before.getAfternoonWorkLocation(), after.afternoonWorkLocation())) continue;
            String oldValue = before == null ? "Aucune activité" : before.getProject().getName()
                    + " / " + locationLabel(before.getWorkLocation());
            String newValue = after == null ? "Aucune activité" : proposedProjects.get(date).getName()
                    + " / " + locationLabel(after.workLocation());
                String parts = "";
                if ((before != null && (before.getDayPart() != com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY || before.getAfternoonProject() != null))
                    || (after != null && (after.dayPart() != com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY || after.afternoonProjectId() != null))) {
                parts = "; créneau : " + (before == null ? "Aucun" : before.getDayPart()) + " -> " + (after == null ? "Aucun" : after.dayPart())
                    + "; projet après-midi : " + (before == null || before.getAfternoonProject() == null ? "Identique" : before.getAfternoonProject().getName())
                    + " -> " + (after == null || after.afternoonProjectId() == null ? "Identique" : projectRepository.getReferenceById(after.afternoonProjectId()).getName())
                    + "; lieu après-midi : " + locationLabel(before == null ? null : before.getAfternoonWorkLocation())
                    + " -> " + locationLabel(after == null ? null : after.afternoonWorkLocation());
                }
                changes.add(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " : " + oldValue + " -> " + newValue + parts);
        }
        User editor = userRepository.findById(currentUserId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        String subject = "Corrections de votre CRA - " + String.format("%02d/%d", declaration.getMonth(), declaration.getYear());
        String body = "Bonjour " + declaration.getCollaborator().getFirstName() + ",\n\n"
                + fullName(editor) + " a corrigé votre déclaration CRA avant validation.\n\n"
                + String.join("\n", changes) + "\n\nCordialement,\n" + fullName(editor);
        return new CraReviewPreviewDto(declaration.getCollaborator().getEmail(), subject, body, changes.size());
    }

    private String locationLabel(com.esn.inodoc.domain.cra.CraWorkLocation location) {
        if (location == null) return "Lieu non renseigné";
        return switch (location) {
            case REMOTE -> "Télétravail";
            case CLIENT_SITE -> "Chez le client";
            case INOCO_OFFICE -> "Chez INOCO";
        };
    }

    @Transactional
    public CraDeclarationDto validate(UUID id) {
        CraDeclaration declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
        if (declaration.getStatus() != CraDeclarationStatus.DECLARED) {
            throw new ConflictException("Seule une déclaration envoyée peut être validée");
        }
        User validator = userRepository.findById(currentUserId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
        if (validator.getId().equals(declaration.getCollaborator().getId()) && !rules.policy().isAllowSelfApproval())
            throw new ConflictException("La validation de son propre CRA est désactivée");
        rules.requireComplete(declaration);
        String before = audit.snapshot(declaration);
        rules.synchronize(declaration);
        declaration.setStatus(CraDeclarationStatus.VALIDATED);
        declaration.setValidatedAt(Instant.now());
        declaration.setValidatedBy(validator);
        declarationRepository.save(declaration);
        audit.record(declaration, "VALIDATED", null, before);
        return toDto(declaration);
    }

    @Transactional(readOnly = true)
        public Page<CraDeclarationDto> listValidated(CraValidatedFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Pagination invalide");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("year"), Sort.Order.desc("month"),
            Sort.Order.asc("collaborator.firstName"), Sort.Order.asc("collaborator.lastName"), Sort.Order.asc("id")));
        var result = declarationRepository.findAll(CraDeclarationSpecifications.validated(filter), pageable);
        if (result.isEmpty()) return result.map(this::toDto);
        var details = declarationRepository.findWithDetailsByIdIn(result.getContent().stream().map(CraDeclaration::getId).toList())
            .stream().collect(Collectors.toMap(CraDeclaration::getId, Function.identity()));
        return result.map(item -> toDto(details.get(item.getId())));
        }

        @Transactional(readOnly = true)
        public CraValidatedFilterOptionsDto validatedFilterOptions() {
        var status = CraDeclarationStatus.VALIDATED;
        return new CraValidatedFilterOptionsDto(declarationRepository.findYearsByStatus(status),
            declarationRepository.findCollaboratorsByStatus(status), declarationRepository.findValidatorsByStatus(status),
            java.util.stream.Stream.concat(declarationRepository.findProjectsByStatus(status).stream(), declarationRepository.findAfternoonProjectsByStatus(status).stream())
                .collect(Collectors.toMap(com.esn.inodoc.dto.cra.CraProjectOptionDto::id, Function.identity(), (first, second) -> first))
                .values().stream().sorted(java.util.Comparator.comparing(com.esn.inodoc.dto.cra.CraProjectOptionDto::name)).toList());
    }

    @Transactional
    public CraDeclarationDto unvalidate(UUID id) {
        CraDeclaration declaration = declarationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Déclaration introuvable"));
        if (declaration.getStatus() != CraDeclarationStatus.VALIDATED) {
            throw new ConflictException("Seule une déclaration validée peut être dévalidée");
        }
        String before = audit.snapshot(declaration);
        declaration.setStatus(CraDeclarationStatus.DECLARED);
        declaration.setValidatedAt(null);
        declaration.setValidatedBy(null);
        rules.synchronize(declaration);
        declarationRepository.save(declaration);
        audit.record(declaration, "UNVALIDATED", "Retour à valider", before);
        return toDto(declaration);
    }

    @Transactional
    public CraDeclarationDto reject(UUID id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new BadRequestException("Un motif de refus est obligatoire");
        CraDeclaration declaration = requireDeclared(id);
        String before = audit.snapshot(declaration);
        declaration.setStatus(CraDeclarationStatus.PENDING);
        declaration.setRejectionReason(reason.trim());
        rules.synchronize(declaration);
        declarationRepository.save(declaration);
        audit.record(declaration, "REJECTED", reason.trim(), before);
        return toDto(declaration);
    }

    private CraDeclaration requireCurrent() {
        YearMonth period = YearMonth.now();
        return declarationRepository.findByCollaborator_IdAndYearAndMonth(
                        currentUserId(), period.getYear(), period.getMonthValue())
                .orElseThrow(() -> new NotFoundException("Aucune déclaration pour ce mois"));
    }

    private CraDeclaration requireEditableCurrent() {
        CraDeclaration declaration = requireCurrent();
        if (declaration.getStatus() != CraDeclarationStatus.PENDING) {
            throw new ConflictException("Seule une déclaration en attente peut être modifiée");
        }
        return declaration;
    }

    private void replaceEntries(CraDeclaration declaration, List<CraDayAssignmentRequest> days) {
        var normalized = rules.normalizeAssignments(declaration, days);
        applyEntries(declaration, normalized, resolveEntries(declaration, normalized));
    }

    private Map<LocalDate, CraProject> resolveEntries(CraDeclaration declaration, List<CraDayAssignmentRequest> days) {
        YearMonth period = YearMonth.of(declaration.getYear(), declaration.getMonth());
        UUID collaboratorId = declaration.getCollaborator().getId();
        Map<UUID, CraProject> accessibleProjects = projectRepository
                .findAvailableForUser(collaboratorId, CraProjectType.CLIENT).stream()
                .collect(Collectors.toMap(CraProject::getId, Function.identity()));
        Set<LocalDate> dates = new HashSet<>();
        var imposed = rules.imposedProjects(declaration);
        Map<LocalDate, CraProject> projects = new java.util.HashMap<>();
        for (CraDayAssignmentRequest request : days) {
            if (request.dayPart() != com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY && request.afternoonProjectId() != null)
                throw new BadRequestException("Un second projet nécessite une journée entière");
            if (request.afternoonWorkLocation() != null && request.afternoonProjectId() == null)
                throw new BadRequestException("Le lieu après-midi nécessite un projet après-midi");
            if (!isInPeriod(request.date(), period)) {
                throw new BadRequestException("Chaque activité doit appartenir au mois de la déclaration");
            }
            if (!dates.add(request.date())) {
                throw new BadRequestException("Un seul projet peut être choisi par jour");
            }
            CraProject project = accessibleProjects.get(request.projectId());
            if (project == null && imposed.containsKey(request.date()) && imposed.get(request.date()).getId().equals(request.projectId()))
                project = imposed.get(request.date());
            if (project == null) {
                project = declaration.getEntries().stream()
                        .filter(entry -> entry.getWorkDate().equals(request.date())
                            && (entry.getProject().getId().equals(request.projectId())
                            || (entry.getAfternoonProject() != null && entry.getAfternoonProject().getId().equals(request.projectId()))))
                        .map(entry -> entry.getProject().getId().equals(request.projectId()) ? entry.getProject() : entry.getAfternoonProject()).findFirst().orElse(null);
            }
            if (project == null) {
                throw new BadRequestException("Ce projet ne vous est pas accessible");
            }
            projects.put(request.date(), project);
            if (request.afternoonProjectId() != null && !accessibleProjects.containsKey(request.afternoonProjectId())) {
                boolean historical = declaration.getEntries().stream().anyMatch(entry -> entry.getWorkDate().equals(request.date())
                        && (entry.getProject().getId().equals(request.afternoonProjectId()) || (entry.getAfternoonProject() != null
                        && entry.getAfternoonProject().getId().equals(request.afternoonProjectId()))));
                if (!historical) throw new BadRequestException("Le projet après-midi n'est pas accessible au collaborateur");
            }
        }
        return projects;
    }

    private void applyEntries(CraDeclaration declaration, List<CraDayAssignmentRequest> days,
                               Map<LocalDate, CraProject> projects) {
        Map<LocalDate, CraDayEntry> existing = declaration.getEntries().stream()
                .collect(Collectors.toMap(CraDayEntry::getWorkDate, Function.identity()));
        declaration.getEntries().removeIf(entry -> !projects.containsKey(entry.getWorkDate()));
        for (CraDayAssignmentRequest request : days) {
            CraDayEntry entry = existing.get(request.date());
            if (entry == null) {
                entry = new CraDayEntry();
                declaration.getEntries().add(entry);
            }
            entry.setDeclaration(declaration);
            entry.setWorkDate(request.date());
            entry.setProject(projects.get(request.date()));
            entry.setWorkLocation(request.workLocation());
            entry.setDayPart(request.dayPart());
            entry.setAfternoonProject(request.afternoonProjectId() == null ? null : projectRepository.getReferenceById(request.afternoonProjectId()));
            entry.setAfternoonWorkLocation(request.afternoonWorkLocation());
        }
    }

    private CraDeclarationDto toDto(CraDeclaration declaration) {
        User collaborator = declaration.getCollaborator();
        User validator = declaration.getValidatedBy();
        return new CraDeclarationDto(declaration.getId(), collaborator.getId(), fullName(collaborator),
                collaborator.getEmail(), declaration.getYear(), declaration.getMonth(), declaration.getStatus(),
                declaration.getDeclaredAt(), declaration.getValidatedAt(), validator == null ? null : fullName(validator),
                rules.effectiveEntries(declaration).stream().map(entry -> new CraEntryDto(entry.getWorkDate(),
                    entry.getProject().getId(), entry.getProject().getName(), entry.getWorkLocation(), entry.getDayPart(),
                    entry.getAfternoonProject() == null ? null : entry.getAfternoonProject().getId(),
                    entry.getAfternoonProject() == null ? null : entry.getAfternoonProject().getName(), entry.getAfternoonWorkLocation(),
                    entry.getProject().getClientName(), entry.getAfternoonProject() == null ? null : entry.getAfternoonProject().getClientName())).toList(),
                declaration.getRejectionReason());
    }

    private CraDeclarationSummaryDto toSummary(CraDeclaration declaration) {
        User collaborator = declaration.getCollaborator();
        return new CraDeclarationSummaryDto(declaration.getId(), collaborator.getId(), fullName(collaborator),
                collaborator.getEmail(), declaration.getYear(), declaration.getMonth(), declaration.getStatus(),
                rules.effectiveEntries(declaration).stream().mapToDouble(entry -> entry.getDayPart().units()).sum());
    }

    private UUID currentUserId() {
        return SecurityUtils.requirePrincipal().getUserId();
    }

    private boolean isInPeriod(LocalDate date, YearMonth period) {
        return YearMonth.from(date).equals(period);
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}