package com.esn.inodoc.service.cra;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.dto.cra.CraOperationsDto;
import com.esn.inodoc.repo.*;
import com.esn.inodoc.repo.cra.*;
import com.esn.inodoc.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraReportingServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final CraDeclarationRepository declarations = mock(CraDeclarationRepository.class);
    final CraReminderRepository reminders = mock(CraReminderRepository.class);
    final MailAccountRepository accounts = mock(MailAccountRepository.class);
    final MailAccountService mail = mock(MailAccountService.class);
    final EmailService emails = mock(EmailService.class);
    final CraRulesService rules = mock(CraRulesService.class);
    final CraAuditService audit = mock(CraAuditService.class);
    final CraFeatureService feature = mock(CraFeatureService.class);
    final CraReportingService service = new CraReportingService(users, declarations, reminders, accounts, mail, emails, rules, audit, feature);
    @org.junit.jupiter.api.AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    User actor() {
        var user = new User(); user.setId(UUID.randomUUID()); user.setFirstName("Finance"); user.setLastName("Test"); user.setEmail("finance@example.test");
        var role = new Role(); role.setName(RoleName.FINANCE_MANAGER); user.setRoles(Set.of(role));
        var principal = com.esn.inodoc.security.UserPrincipal.from(user);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return user;
    }
    @Test void queuedRemindersAreOwnedTargetedAndIdempotentForTheSameDay() {
        var actor = actor(); var owner = actor();
        var principal = com.esn.inodoc.security.UserPrincipal.from(actor);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var period = YearMonth.now(); var account = new MailAccount(); account.setId(UUID.randomUUID()); account.setCreatedBy(actor.getId());
        when(mail.requireActive(account.getId())).thenReturn(account);
        when(users.lockForCraReminder(owner.getId())).thenReturn(Optional.of(owner));
        when(rules.period(period.getYear(), period.getMonthValue())).thenReturn(period);
        when(rules.calendar(owner.getId(), period)).thenReturn(new CraOperationsDto.Calendar(List.of(new CraOperationsDto.CalendarDay(period.atDay(1), true, true, false)), 1));
        when(reminders.existsByUserIdAndYearAndMonthAndQueuedDate(owner.getId(), period.getYear(), period.getMonthValue(), LocalDate.now())).thenReturn(false, true);
        var request = new CraOperationsDto.ReminderRequest(List.of(owner.getId()), account.getId());
        assertEquals(1, service.queue(period.getYear(), period.getMonthValue(), request));
        assertEquals(0, service.queue(period.getYear(), period.getMonthValue(), request));
        var item = org.mockito.ArgumentCaptor.forClass(CraReminder.class); verify(reminders).save(item.capture());
        assertEquals(actor.getId(), item.getValue().getRequestedBy()); assertEquals(owner.getId(), item.getValue().getUserId());
        assertEquals("QUEUED", item.getValue().getState()); verifyNoInteractions(emails);
    }
    @Test void inaccessibleSenderPreventsQueueCreation() {
        actor(); UUID account = UUID.randomUUID();
        when(rules.period(2026, 1)).thenReturn(YearMonth.of(2026, 1));
        when(mail.requireActive(account)).thenThrow(new org.springframework.security.access.AccessDeniedException("Compte étranger"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.queue(2026, 1, new CraOperationsDto.ReminderRequest(List.of(UUID.randomUUID()), account)));
        verifyNoInteractions(reminders, emails);
    }
    @Test void permanentFailuresStopAfterThreeAttempts() {
        when(feature.isEnabled()).thenReturn(true); var item = new CraReminder(); item.setUserId(UUID.randomUUID()); item.setRequestedBy(UUID.randomUUID());
        when(reminders.findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(eq("QUEUED"), any())).thenReturn(List.of(item));
        service.dispatch(); service.dispatch(); service.dispatch();
        assertEquals(3, item.getAttempts()); assertEquals("FAILED", item.getState());
    }
        @Test void monitoringCountsAutomaticActivitiesButNotNonWorkingDates() {
        var period = YearMonth.now().minusMonths(1); var user = actor();
        when(rules.period(period.getYear(), period.getMonthValue())).thenReturn(period);
        when(users.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<User>>any(),
            any(org.springframework.data.domain.Pageable.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(user)));
        when(rules.calendar(user.getId(), period)).thenReturn(new CraOperationsDto.Calendar(List.of(
            new CraOperationsDto.CalendarDay(period.atDay(1), true, true, false),
            new CraOperationsDto.CalendarDay(period.atDay(2), false, false, true),
            new CraOperationsDto.CalendarDay(period.atDay(3), true, true, false)), 2));
        var rtt = new CraDayEntry(); rtt.setWorkDate(period.atDay(1)); rtt.setDayPart(CraDayPart.FULL_DAY);
        when(rules.effectiveEntries(any())).thenReturn(List.of(rtt));
        var row = service.monitor(period.getYear(), period.getMonthValue(), "", "", 0, 25).getContent().get(0);
        assertEquals(2.0, row.expectedUnits()); assertEquals(1.0, row.assignedUnits()); assertEquals(1.0, row.missingUnits());
        assertEquals("MISSING", row.status());
        }
        @Test void statisticsAggregateValidatedDaysAndDailyRateByClientAndProject() {
        var period = YearMonth.now().minusMonths(1); var user = actor();
        var profile = new Profile(); profile.setDailyRate(650); user.setProfile(profile);
        var declaration = new CraDeclaration(); declaration.setCollaborator(user);
        declaration.setYear(period.getYear()); declaration.setMonth(period.getMonthValue());
        declaration.setStatus(CraDeclarationStatus.VALIDATED);
        var project = new CraProject(); project.setName("Atlas"); project.setClientName("Acme");
        var entry = new CraDayEntry(); entry.setDeclaration(declaration); entry.setWorkDate(period.atDay(1));
        entry.setProject(project); entry.setDayPart(CraDayPart.FULL_DAY); declaration.getEntries().add(entry);
        when(rules.period(period.getYear(), period.getMonthValue())).thenReturn(period);
        when(users.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<User>>any(),
            any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(user)));
        when(declarations.findMonthlyForUsers(period.getYear(), period.getMonthValue(), List.of(user.getId())))
            .thenReturn(List.of(declaration));
        when(rules.calendar(user.getId(), period)).thenReturn(new CraOperationsDto.Calendar(
            List.of(new CraOperationsDto.CalendarDay(period.atDay(1), true, true, false)), 1));
        when(rules.effectiveEntries(any())).thenReturn(List.of(entry));

        var statistics = service.statistics(period.getYear(), period.getMonthValue());

        assertEquals(1, statistics.collaboratorCount()); assertEquals(1, statistics.declaredCount());
        assertEquals(1.0, statistics.expectedUnits()); assertEquals(1.0, statistics.assignedUnits());
        assertEquals(0.0, statistics.missingUnits()); assertEquals(100.0, statistics.completionPercent());
        assertEquals(650.0, statistics.validatedAmount());
        assertEquals(650.0, statistics.revenueByClient().get(0).amount());
        assertEquals("Atlas", statistics.revenueByProject().get(0).label());
        }
        @Test void disabledFeatureNeverDispatchesMail() { service.dispatch(); verifyNoInteractions(reminders, emails); }
    @Test void submittedCraIsNotRemindedEvenIfItWasMissingWhenQueued() {
        when(feature.isEnabled()).thenReturn(true);
        var item = new CraReminder(); item.setUserId(UUID.randomUUID()); item.setRequestedBy(UUID.randomUUID());
        var user = new User(); user.setId(item.getUserId());
        var reviewer = new User(); reviewer.setId(item.getRequestedBy()); var role = new Role(); role.setName(RoleName.FINANCE_MANAGER); reviewer.setRoles(Set.of(role));
        when(users.findById(user.getId())).thenReturn(Optional.of(user)); when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        when(reminders.findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(eq("QUEUED"), any())).thenReturn(List.of(item));
        var declared = new CraDeclaration(); declared.setStatus(CraDeclarationStatus.DECLARED);
        when(declarations.findByCollaborator_IdAndYearAndMonth(user.getId(), 0, 0)).thenReturn(Optional.of(declared));
        service.dispatch(); assertEquals("SKIPPED", item.getState()); verifyNoInteractions(emails);
    }
    @Test void failedDispatchHasABoundedRetryAndNoSecretsInTheError() {
        when(feature.isEnabled()).thenReturn(true);
        var item = new CraReminder(); item.setUserId(UUID.randomUUID()); item.setRequestedBy(UUID.randomUUID()); item.setAttempts(3);
        when(reminders.findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(eq("QUEUED"), any())).thenReturn(List.of(item));
        service.dispatch(); assertEquals("FAILED", item.getState()); assertFalse(item.getError().contains("token"));
    }
}