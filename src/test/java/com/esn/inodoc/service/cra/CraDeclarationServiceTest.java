package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraDayEntry;
import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.domain.cra.CraWorkLocation;
import com.esn.inodoc.dto.cra.CraDayAssignmentRequest;
import com.esn.inodoc.dto.cra.CraEntriesRequest;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.CraDeclarationRepository;
import com.esn.inodoc.repo.cra.CraProjectRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.service.CraFeatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraDeclarationServiceTest {
    private final CraDeclarationRepository declarations = mock(CraDeclarationRepository.class);
    private final CraProjectRepository projects = mock(CraProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CraFeatureService feature = mock(CraFeatureService.class);
        private final com.esn.inodoc.service.MailAccountService mailAccounts = mock(com.esn.inodoc.service.MailAccountService.class);
        private final com.esn.inodoc.service.EmailService emails = mock(com.esn.inodoc.service.EmailService.class);
    private final CraRulesService rules = mock(CraRulesService.class);
    private final CraAuditService audit = mock(CraAuditService.class);
    {
        when(rules.period(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> YearMonth.of(call.<Integer>getArgument(0), call.<Integer>getArgument(1)));
        when(rules.policy()).thenReturn(new com.esn.inodoc.domain.cra.CraPolicy());
        when(rules.effectiveEntries(any())).thenAnswer(call -> List.copyOf(call.getArgument(0, CraDeclaration.class).getEntries()));
        when(rules.normalizeAssignments(any(), any())).thenAnswer(call -> call.getArgument(1));
    }
        private final CraDeclarationService service = new CraDeclarationService(declarations, projects, users, feature,
            mailAccounts, emails, rules, audit);
    private final UUID employeeId = UUID.randomUUID();
    private final User employee = user(employeeId, RoleName.INTERNAL);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsOnlyOnePendingDeclarationForTheCurrentMonth() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.empty());
        when(users.findById(employeeId)).thenReturn(Optional.of(employee));
        when(declarations.save(any(CraDeclaration.class))).thenAnswer(call -> {
            CraDeclaration saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var created = service.createCurrent();

        assertEquals(CraDeclarationStatus.PENDING, created.status());
        assertEquals(employeeId, created.collaboratorId());
    }

    @Test
    void rejectsDuplicateCurrentMonthDeclaration() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.of(declaration(employee, current.getYear(), current.getMonthValue(),
                        CraDeclarationStatus.PENDING)));

        assertThrows(ConflictException.class, service::createCurrent);
    }

    @Test
    void returnsSavedCurrentMonthDraftForDisplayAfterReload() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        CraDeclaration draft = declaration(employee, current.getYear(), current.getMonthValue(),
                CraDeclarationStatus.PENDING);
        CraProject project = project("ACCESS_ALL", "Formation interne");
        draft.setEntries(List.of(entry(draft, current.atDay(1), project)));
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.of(draft));

        var loaded = service.getCurrent();

        assertEquals(draft.getId(), loaded.id());
        assertEquals(CraDeclarationStatus.PENDING, loaded.status());
        assertEquals(project.getId(), loaded.days().get(0).projectId());
    }

        @Test
        void listsAndOpensOnlyDeclarationsOwnedByTheCurrentEmployee() {
        signIn(employee);
        CraDeclaration archived = declaration(employee, 2025, 12, CraDeclarationStatus.VALIDATED);
        when(declarations.findByCollaborator_IdOrderByYearDescMonthDesc(employeeId))
            .thenReturn(List.of(archived));
        when(declarations.findByIdAndCollaborator_Id(archived.getId(), employeeId))
            .thenReturn(Optional.of(archived));

        var history = service.listMine();
        var opened = service.getMine(archived.getId());

        assertEquals(List.of(archived.getId()), history.stream().map(item -> item.id()).toList());
        assertEquals(archived.getId(), opened.id());
        assertThrows(com.esn.inodoc.controller.error.NotFoundException.class,
            () -> service.getMine(UUID.randomUUID()));
        }

    @Test
    void savesOneAccessibleProjectPerDateWithinTheCurrentMonth() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        CraDeclaration declaration = declaration(employee, current.getYear(), current.getMonthValue(),
                CraDeclarationStatus.PENDING);
        CraProject project = project("ACCESS_ALL", "Formation interne");
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.of(declaration));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(project));
        when(declarations.save(any(CraDeclaration.class))).thenAnswer(call -> call.getArgument(0));

        var saved = service.saveCurrentEntries(new CraEntriesRequest(List.of(
            new CraDayAssignmentRequest(current.atDay(1), project.getId(), CraWorkLocation.REMOTE))));

        assertEquals(1, saved.days().size());
        assertEquals(project.getId(), saved.days().get(0).projectId());
        assertEquals(CraWorkLocation.REMOTE, saved.days().get(0).workLocation());
    }

        @Test
        void savingDraftReplacesTheCompleteSetOfDayAssignments() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        CraDeclaration declaration = declaration(employee, current.getYear(), current.getMonthValue(),
            CraDeclarationStatus.PENDING);
        CraProject oldProject = project("CLIENT_OLD", "Ancien projet");
        CraProject currentProject = project("ACCESS_ALL", "Formation interne");
        LocalDate firstDate = current.atDay(1);
        LocalDate secondDate = current.atDay(Math.min(2, current.lengthOfMonth()));
        declaration.setEntries(new ArrayList<>(List.of(
            entry(declaration, firstDate, oldProject),
            entry(declaration, secondDate, oldProject))));
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
            .thenReturn(Optional.of(declaration));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(currentProject));
        when(declarations.save(any(CraDeclaration.class))).thenAnswer(call -> call.getArgument(0));

        var saved = service.saveCurrentEntries(new CraEntriesRequest(List.of(
            new CraDayAssignmentRequest(firstDate, currentProject.getId(), null))));

        assertEquals(1, saved.days().size());
        assertEquals(firstDate, saved.days().get(0).date());
        assertEquals(currentProject.getId(), saved.days().get(0).projectId());
        assertEquals(null, saved.days().get(0).workLocation());
        }

    @Test
        void rejectsDatesOutsideTheCurrentMonthAndInaccessibleProjects() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        CraDeclaration declaration = declaration(employee, current.getYear(), current.getMonthValue(),
            CraDeclarationStatus.PENDING);
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.of(declaration));
        assertThrows(BadRequestException.class, () -> service.saveCurrentEntries(new CraEntriesRequest(List.of(
            new CraDayAssignmentRequest(current.plusMonths(1).atDay(1), UUID.randomUUID(), null)))));

        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.saveCurrentEntries(new CraEntriesRequest(List.of(
            new CraDayAssignmentRequest(current.atDay(1), UUID.randomUUID(), null)))));
    }

    @Test
    void onlySubmitsWhenElapsedWeekdaysHaveAssignments() {
        signIn(employee);
        YearMonth current = YearMonth.now();
        CraDeclaration declaration = declaration(employee, current.getYear(), current.getMonthValue(),
            CraDeclarationStatus.PENDING);
        when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue()))
                .thenReturn(Optional.of(declaration));
        org.mockito.Mockito.doThrow(new BadRequestException("Activités manquantes")).doNothing().when(rules).requireComplete(declaration);
        assertThrows(BadRequestException.class, service::submitCurrent);

        CraProject project = project("ACCESS_ALL", "Congés payés");
        List<CraDayEntry> entries = new ArrayList<>();
        for (int day = 1; day <= LocalDate.now().getDayOfMonth(); day++) {
            LocalDate date = current.atDay(day);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                entries.add(entry(declaration, date, project));
            }
        }
        declaration.setEntries(entries);
        when(declarations.save(any(CraDeclaration.class))).thenAnswer(call -> call.getArgument(0));

        var submitted = service.submitCurrent();

        assertEquals(CraDeclarationStatus.DECLARED, submitted.status());
    }

    @Test
    void reviewerCanValidateOnlyDeclaredDeclarations() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declaration = declaration(employee, 2025, 2, CraDeclarationStatus.DECLARED);
        when(declarations.findById(declaration.getId())).thenReturn(Optional.of(declaration));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(declarations.save(any(CraDeclaration.class))).thenAnswer(call -> call.getArgument(0));

        var validated = service.validate(declaration.getId());

        assertEquals(CraDeclarationStatus.VALIDATED, validated.status());
        assertEquals("Collaborateur Test", validated.validatedByName());
    }

    @Test
    void reviewQueueContainsSubmittedDeclarationsButNotDraftsOrValidatedItems() {
        CraDeclaration submitted = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        when(declarations.findForReview(CraDeclarationStatus.DECLARED)).thenReturn(List.of(submitted));

        var queue = service.listForReview();

        assertEquals(List.of(CraDeclarationStatus.DECLARED), queue.stream().map(item -> item.status()).toList());
    }

    @Test
    void financeManagerCannotValidateAnUnsubmittedDraft() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration draft = declaration(employee, 2026, 5, CraDeclarationStatus.PENDING);
        when(declarations.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        assertThrows(ConflictException.class, () -> service.validate(draft.getId()));
    }

    private CraDeclaration declaration(User collaborator, int year, int month, CraDeclarationStatus status) {
        CraDeclaration declaration = new CraDeclaration();
        declaration.setId(UUID.randomUUID());
        declaration.setCollaborator(collaborator);
        declaration.setYear(year);
        declaration.setMonth(month);
        declaration.setStatus(status);
        return declaration;
    }

    @Test
    void previewListsProjectLocationAdditionAndRemovalWithoutSavingOrSending() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        reviewer.setFirstName("Finance");
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject oldProject = project("CLIENT_OLD", "Ancien client");
        CraProject newProject = project("CLIENT_NEW", "Nouveau client");
        CraDayEntry first = entry(declared, LocalDate.of(2026, 4, 1), oldProject);
        first.setWorkLocation(CraWorkLocation.CLIENT_SITE);
        declared.getEntries().add(first);
        declared.getEntries().add(entry(declared, LocalDate.of(2026, 4, 2), oldProject));
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(newProject));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));

        var preview = service.previewReviewChanges(declared.getId(), new CraEntriesRequest(List.of(
                new CraDayAssignmentRequest(LocalDate.of(2026, 4, 1), newProject.getId(), CraWorkLocation.REMOTE),
                new CraDayAssignmentRequest(LocalDate.of(2026, 4, 3), newProject.getId(), null))));

        assertEquals(employee.getEmail(), preview.recipient());
        assertEquals(3, preview.changedDays());
        org.junit.jupiter.api.Assertions.assertTrue(preview.body().contains("Ancien client / Chez le client -> Nouveau client / Télétravail"));
        org.junit.jupiter.api.Assertions.assertTrue(preview.body().contains("02/04/2026 : Ancien client / Lieu non renseigné -> Aucune activité"));
        org.junit.jupiter.api.Assertions.assertTrue(preview.body().contains("03/04/2026 : Aucune activité -> Nouveau client"));
        org.junit.jupiter.api.Assertions.assertTrue(preview.body().contains("Finance Test"));
        assertEquals(oldProject, first.getProject());
        org.mockito.Mockito.verifyNoInteractions(emails, mailAccounts);
        verify(declarations, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void savesCorrectionsWithoutEmailAndKeepsDeclaredStatusAndExistingDayEntity() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject assigned = project("CLIENT_1", "Client");
        CraDayEntry day = entry(declared, LocalDate.of(2026, 4, 1), assigned);
        declared.getEntries().add(day);
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(assigned));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));

        var saved = service.saveReviewChanges(declared.getId(), new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(
                List.of(new CraDayAssignmentRequest(day.getWorkDate(), assigned.getId(), CraWorkLocation.INOCO_OFFICE)), null));

        assertEquals(CraDeclarationStatus.DECLARED, saved.status());
        assertEquals(CraWorkLocation.INOCO_OFFICE, saved.days().get(0).workLocation());
        org.junit.jupiter.api.Assertions.assertSame(day, declared.getEntries().get(0));
        org.mockito.Mockito.verifyNoInteractions(emails, mailAccounts);
    }

    @Test
    void sendsComputedChangesOnlyWhenAnOwnedMailAccountIsExplicitlyChosen() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject assigned = project("CLIENT_1", "Client");
        var account = new com.esn.inodoc.domain.MailAccount();
        account.setId(UUID.randomUUID());
        account.setCreatedBy(reviewer.getId());
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(assigned));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(mailAccounts.requireActive(account.getId())).thenReturn(account);
        when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));

        service.saveReviewChanges(declared.getId(), new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(
                List.of(new CraDayAssignmentRequest(LocalDate.of(2026, 4, 1), assigned.getId(), null)), account.getId()));

        verify(mailAccounts).requireActive(account.getId());
        verify(emails).send(org.mockito.ArgumentMatchers.same(account), org.mockito.ArgumentMatchers.eq(employee.getEmail()),
                org.mockito.ArgumentMatchers.contains("04/2026"), org.mockito.ArgumentMatchers.contains("01/04/2026"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void rejectsCorrectionsToDraftsAndValidatedDeclarations() {
        for (var status : List.of(CraDeclarationStatus.PENDING, CraDeclarationStatus.VALIDATED)) {
            CraDeclaration declaration = declaration(employee, 2026, 4, status);
            when(declarations.findById(declaration.getId())).thenReturn(Optional.of(declaration));
            assertThrows(ConflictException.class, () -> service.saveReviewChanges(declaration.getId(),
                    new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(List.of(), null)));
        }
        org.mockito.Mockito.verifyNoInteractions(emails, mailAccounts);
    }

    @Test
    void correctionsCannotAssignProjectsUnavailableToTheDeclarationOwner() {
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.saveReviewChanges(declared.getId(),
                new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(List.of(
                        new CraDayAssignmentRequest(LocalDate.of(2026, 4, 1), UUID.randomUUID(), null)), null)));
        verify(projects).findAvailableForUser(employeeId, CraProjectType.CLIENT);
        verify(declarations, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void emailFailureDoesNotApplyCorrectionsToTheDeclaration() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject assigned = project("CLIENT_1", "Client");
        UUID accountId = UUID.randomUUID();
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(assigned));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(mailAccounts.requireActive(accountId)).thenThrow(new org.springframework.security.access.AccessDeniedException("Compte étranger"));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.saveReviewChanges(
                declared.getId(), new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(List.of(
                        new CraDayAssignmentRequest(LocalDate.of(2026, 4, 1), assigned.getId(), null)), accountId)));

        assertEquals(0, declared.getEntries().size());
        org.mockito.Mockito.verifyNoInteractions(emails);
        verify(declarations, org.mockito.Mockito.never()).save(any());
    }

        @Test
        void reviewProjectsUseTheOwnerAndKeepHistoricalProjectsOnlyOnTheirOriginalDates() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject oldProject = project("CLIENT_OLD", "Ancien client");
        CraProject available = project("CLIENT_CURRENT", "Client actuel");
        CraDayEntry original = entry(declared, LocalDate.of(2026, 4, 1), oldProject);
        declared.getEntries().add(original);
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(available));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));

        var choices = service.projectsForReview(declared.getId());
        assertEquals(List.of(oldProject.getId(), available.getId()), choices.stream().map(item -> item.id()).toList());
        verify(projects).findAvailableForUser(employeeId, CraProjectType.CLIENT);
        var saved = service.saveReviewChanges(declared.getId(), new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(
            List.of(new CraDayAssignmentRequest(original.getWorkDate(), oldProject.getId(), CraWorkLocation.REMOTE)), null));
        assertEquals(oldProject.getId(), saved.days().get(0).projectId());
        org.junit.jupiter.api.Assertions.assertSame(original, declared.getEntries().get(0));
        assertThrows(BadRequestException.class, () -> service.previewReviewChanges(declared.getId(), new CraEntriesRequest(
            List.of(new CraDayAssignmentRequest(LocalDate.of(2026, 4, 2), oldProject.getId(), null)))));
        }

        @Test
        void unchangedCorrectionsDoNotSaveOrSendAnEmail() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject assigned = project("CLIENT_1", "Client");
        declared.getEntries().add(entry(declared, LocalDate.of(2026, 4, 1), assigned));
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(assigned));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));

        var days = List.of(new CraDayAssignmentRequest(LocalDate.of(2026, 4, 1), assigned.getId(), null));
        assertEquals(0, service.previewReviewChanges(declared.getId(), new CraEntriesRequest(days)).changedDays());
        assertThrows(BadRequestException.class, () -> service.saveReviewChanges(declared.getId(),
            new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(days, UUID.randomUUID())));
        org.mockito.Mockito.verifyNoInteractions(emails, mailAccounts);
        verify(declarations, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void gmailDeliveryFailurePreservesTheOriginalEntries() {
        User reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
        signIn(reviewer);
        CraDeclaration declared = declaration(employee, 2026, 4, CraDeclarationStatus.DECLARED);
        CraProject assigned = project("CLIENT_1", "Client");
        CraDayEntry original = entry(declared, LocalDate.of(2026, 4, 1), assigned);
        declared.getEntries().add(original);
        var account = new com.esn.inodoc.domain.MailAccount();
        account.setId(UUID.randomUUID());
        when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
        when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(assigned));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(mailAccounts.requireActive(account.getId())).thenReturn(account);
        org.mockito.Mockito.doThrow(new IllegalStateException("Gmail unavailable"))
            .when(emails).send(any(), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> service.saveReviewChanges(declared.getId(),
            new com.esn.inodoc.dto.cra.CraReviewUpdateRequest(List.of(new CraDayAssignmentRequest(
                original.getWorkDate(), assigned.getId(), CraWorkLocation.REMOTE)), account.getId())));
        assertEquals(null, original.getWorkLocation());
        org.junit.jupiter.api.Assertions.assertSame(original, declared.getEntries().get(0));
        verify(declarations, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void validatedArchiveContainsOnlyValidatedDeclarationsAndTheirValidationMetadata() {
            CraDeclaration validated = declaration(employee, 2025, 12, CraDeclarationStatus.VALIDATED);
            validated.setValidatedAt(java.time.Instant.parse("2026-01-02T10:00:00Z"));
            validated.setValidatedBy(user(UUID.randomUUID(), RoleName.FINANCE_MANAGER));
            when(declarations.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(validated), org.springframework.data.domain.PageRequest.of(0, 25), 10001));
            when(declarations.findWithDetailsByIdIn(List.of(validated.getId()))).thenReturn(List.of(validated));

            var archive = service.listValidated(com.esn.inodoc.dto.cra.CraValidatedFilter.empty(), 0, 25);

            assertEquals(10001, archive.getTotalElements());
            assertEquals(25, archive.getSize());
            assertEquals(1, archive.getContent().size());
            assertEquals(CraDeclarationStatus.VALIDATED, archive.getContent().get(0).status());
            assertEquals(validated.getValidatedAt(), archive.getContent().get(0).validatedAt());
            assertEquals("Collaborateur Test", archive.getContent().get(0).validatedByName());
            verify(declarations).findWithDetailsByIdIn(List.of(validated.getId()));
            verify(declarations, org.mockito.Mockito.never()).findForReview(CraDeclarationStatus.VALIDATED);
        }

        @Test
        void emptyValidatedPagesDoNotLoadActivitiesAndInvalidPaginationIsRejected() {
            when(declarations.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 25)));
            assertEquals(0, service.listValidated(com.esn.inodoc.dto.cra.CraValidatedFilter.empty(), 0, 25).getTotalElements());
            verify(declarations, org.mockito.Mockito.never()).findWithDetailsByIdIn(any());
            for (int[] paging : List.of(new int[]{-1, 25}, new int[]{0, 0}, new int[]{0, 101})) {
                assertThrows(BadRequestException.class, () -> service.listValidated(
                        com.esn.inodoc.dto.cra.CraValidatedFilter.empty(), paging[0], paging[1]));
            }
        }

        @Test
        void filterOptionsAreLoadedWithoutFetchingDeclarationsOrActivities() {
            var status = CraDeclarationStatus.VALIDATED;
            when(declarations.findYearsByStatus(status)).thenReturn(List.of(2026));
            when(declarations.findCollaboratorsByStatus(status)).thenReturn(List.of());
            when(declarations.findValidatorsByStatus(status)).thenReturn(List.of("Finance Test"));
            when(declarations.findProjectsByStatus(status)).thenReturn(List.of());
            var options = service.validatedFilterOptions();
            assertEquals(List.of(2026), options.years());
            assertEquals(List.of("Finance Test"), options.validators());
            verify(declarations, org.mockito.Mockito.never()).findForReview(any());
            verify(declarations, org.mockito.Mockito.never()).findWithDetailsByIdIn(any());
        }

        @Test
        void unvalidationReturnsToReviewAndPreservesActivitiesAndSubmissionDate() {
            CraDeclaration validated = declaration(employee, 2025, 12, CraDeclarationStatus.VALIDATED);
            var declaredAt = java.time.Instant.parse("2026-01-01T10:00:00Z");
            validated.setDeclaredAt(declaredAt);
            validated.setValidatedAt(declaredAt.plusSeconds(3600));
            validated.setValidatedBy(user(UUID.randomUUID(), RoleName.FINANCE_MANAGER));
            CraDayEntry day = entry(validated, LocalDate.of(2025, 12, 1), project("CLIENT_1", "Mission"));
            day.setWorkLocation(CraWorkLocation.REMOTE);
            validated.getEntries().add(day);
            when(declarations.findById(validated.getId())).thenReturn(Optional.of(validated));
            when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));

            var reopened = service.unvalidate(validated.getId());

            assertEquals(CraDeclarationStatus.DECLARED, reopened.status());
            assertEquals(declaredAt, reopened.declaredAt());
            org.junit.jupiter.api.Assertions.assertNull(reopened.validatedAt());
            org.junit.jupiter.api.Assertions.assertNull(reopened.validatedByName());
            org.junit.jupiter.api.Assertions.assertNull(validated.getValidatedBy());
            org.junit.jupiter.api.Assertions.assertSame(day, validated.getEntries().get(0));
            assertEquals(CraWorkLocation.REMOTE, reopened.days().get(0).workLocation());
            org.mockito.Mockito.verifyNoInteractions(emails, mailAccounts);
        }

        @Test
        void unvalidationRejectsDraftsAndAlreadyDeclaredItemsWithoutSaving() {
            for (var status : List.of(CraDeclarationStatus.PENDING, CraDeclarationStatus.DECLARED)) {
                CraDeclaration item = declaration(employee, 2025, 12, status);
                when(declarations.findById(item.getId())).thenReturn(Optional.of(item));
                assertThrows(ConflictException.class, () -> service.unvalidate(item.getId()));
                assertEquals(status, item.getStatus());
            }
            verify(declarations, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void unvalidationRejectsUnknownDeclarations() {
            UUID missingId = UUID.randomUUID();
            when(declarations.findById(missingId)).thenReturn(Optional.empty());
            assertThrows(com.esn.inodoc.controller.error.NotFoundException.class, () -> service.unvalidate(missingId));
            verify(declarations, org.mockito.Mockito.never()).save(any());
        }

        @Test
        void rejectionRequiresAReasonReopensAnIndividualDraftAndIsAudited() {
            var reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER); signIn(reviewer);
            var declared = declaration(employee, 2025, 12, CraDeclarationStatus.DECLARED);
            when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
            when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));
            assertThrows(BadRequestException.class, () -> service.reject(declared.getId(), "  "));
            var rejected = service.reject(declared.getId(), "  Corriger le projet  ");
            assertEquals(CraDeclarationStatus.PENDING, rejected.status());
            assertEquals("Corriger le projet", rejected.rejectionReason());
            verify(audit).record(org.mockito.ArgumentMatchers.same(declared), org.mockito.ArgumentMatchers.eq("REJECTED"), org.mockito.ArgumentMatchers.eq("Corriger le projet"), any());
        }

        @Test
        void existingDeclarationsCanBeReturnedForCorrectionWithoutOpeningAPeriod() {
            var declared = declaration(employee, 2025, 12, CraDeclarationStatus.DECLARED);
            when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
            when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));
            assertEquals(CraDeclarationStatus.PENDING, service.reject(declared.getId(), "Correction").status());
        }

        @Test
        void creationIsRestrictedToTheCurrentMonth() {
            signIn(employee);
            for (var period : List.of(YearMonth.now().minusMonths(1), YearMonth.now().plusMonths(1))) {
                assertThrows(BadRequestException.class, () -> service.create(period.getYear(), period.getMonthValue()));
            }
            org.mockito.Mockito.verifyNoInteractions(declarations);
        }

        @Test
        void ownValidationIsControlledByTheCompanyPolicy() {
            var reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER); signIn(reviewer);
            var declared = declaration(reviewer, 2026, 1, CraDeclarationStatus.DECLARED);
            when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
            when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
            when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));
            assertThrows(ConflictException.class, () -> service.validate(declared.getId()));
            var policy = new com.esn.inodoc.domain.cra.CraPolicy(); policy.setAllowSelfApproval(true);
            when(rules.policy()).thenReturn(policy);
            assertEquals(CraDeclarationStatus.VALIDATED, service.validate(declared.getId()).status());
        }

        @Test
        void splitDaysUseOnlyOwnerProjectsAndHalfDaysCountAsHalfAUnit() {
            signIn(employee); var current = YearMonth.now();
            var draft = declaration(employee, current.getYear(), current.getMonthValue(), CraDeclarationStatus.PENDING);
            var morning = project("CLIENT_A", "Matin"); var afternoon = project("CLIENT_B", "Après-midi");
            when(declarations.findByCollaborator_IdAndYearAndMonth(employeeId, current.getYear(), current.getMonthValue())).thenReturn(Optional.of(draft));
            when(declarations.findByCollaborator_IdOrderByYearDescMonthDesc(employeeId)).thenReturn(List.of(draft));
            when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(morning, afternoon));
            when(projects.getReferenceById(afternoon.getId())).thenReturn(afternoon);
            when(declarations.save(any())).thenAnswer(call -> call.getArgument(0));
            var full = new CraDayAssignmentRequest(current.atDay(1), morning.getId(), CraWorkLocation.REMOTE,
                    com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY, afternoon.getId(), CraWorkLocation.CLIENT_SITE);
            assertEquals(afternoon.getId(), service.saveCurrentEntries(new CraEntriesRequest(List.of(full))).days().get(0).afternoonProjectId());
            var half = new CraDayAssignmentRequest(current.atDay(1), morning.getId(), null,
                    com.esn.inodoc.domain.cra.CraDayPart.MORNING, null, null);
            service.saveCurrentEntries(new CraEntriesRequest(List.of(half)));
            assertEquals(0.5, service.listMine().get(0).assignedDays());
            assertThrows(BadRequestException.class, () -> service.saveCurrentEntries(new CraEntriesRequest(List.of(
                    new CraDayAssignmentRequest(current.atDay(1), morning.getId(), null, com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY, UUID.randomUUID(), null)))));
        }

        @Test
        void missingExpectedActivitiesPreventValidationWithoutSavingOrAuditing() {
            var reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER);
            signIn(reviewer);
            var declared = declaration(employee, 2026, 1, CraDeclarationStatus.DECLARED);
            when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
            when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
            org.mockito.Mockito.doThrow(new BadRequestException("Demi-journées attendues manquantes"))
                    .when(rules).requireComplete(declared);

            assertThrows(BadRequestException.class, () -> service.validate(declared.getId()));

            assertEquals(CraDeclarationStatus.DECLARED, declared.getStatus());
            org.junit.jupiter.api.Assertions.assertNull(declared.getValidatedAt());
            org.junit.jupiter.api.Assertions.assertNull(declared.getValidatedBy());
            verify(declarations, org.mockito.Mockito.never()).save(any());
            org.mockito.Mockito.verifyNoInteractions(audit);
        }

        @Test
        void correctionPreviewIncludesRuleChangesAgainstThePersistedActivities() {
            var reviewer = user(UUID.randomUUID(), RoleName.FINANCE_MANAGER); signIn(reviewer);
            var declared = declaration(employee, 2026, 6, CraDeclarationStatus.DECLARED);
            var client = project("CLIENT_A", "Mission client"); var rtt = project("RTT_EMPLOYER", "RTT employeur");
            declared.getEntries().add(entry(declared, LocalDate.of(2026, 6, 22), client));
            declared.getEntries().add(entry(declared, LocalDate.of(2026, 6, 30), client));
            when(declarations.findById(declared.getId())).thenReturn(Optional.of(declared));
            when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
            when(projects.findAvailableForUser(employeeId, CraProjectType.CLIENT)).thenReturn(List.of(client, rtt));
            when(rules.effectiveEntries(declared)).thenReturn(List.of(entry(declared, LocalDate.of(2026, 6, 22), rtt)));
            var manual = new CraDayAssignmentRequest(LocalDate.of(2026, 6, 23), client.getId(), null);
            when(rules.normalizeAssignments(declared, List.of(manual))).thenReturn(List.of(
                    new CraDayAssignmentRequest(LocalDate.of(2026, 6, 22), rtt.getId(), null), manual));
            var preview = service.previewReviewChanges(declared.getId(), new CraEntriesRequest(List.of(manual)));
            assertEquals(3, preview.changedDays());
            assertTrue(preview.body().contains("RTT employeur"));
            assertTrue(preview.body().contains("30/06/2026 : Mission client"));
            verify(declarations, org.mockito.Mockito.never()).save(any());
        }

        private CraDayEntry entry(CraDeclaration declaration, LocalDate date, CraProject project) {
        CraDayEntry entry = new CraDayEntry();
        entry.setDeclaration(declaration);
        entry.setWorkDate(date);
        entry.setProject(project);
        return entry;
    }

    private CraProject project(String code, String name) {
        CraProject project = new CraProject();
        project.setId(UUID.randomUUID());
        project.setCode(code);
        project.setName(name);
        project.setType(code.startsWith("CLIENT") ? CraProjectType.CLIENT : CraProjectType.ACCESS_ALL);
        return project;
    }

    private User user(UUID id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setFirstName(roleName == RoleName.FINANCE_MANAGER ? "Collaborateur" : "Collaborateur");
        user.setLastName("Test");
        user.setEmail("user@example.test");
        user.setRoles(Set.of(role));
        return user;
    }

    private void signIn(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}