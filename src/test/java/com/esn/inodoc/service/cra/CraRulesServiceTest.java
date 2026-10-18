package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.*;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.dto.cra.CraOperationsDto;
import com.esn.inodoc.repo.cra.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraRulesServiceTest {
    final CraPolicyRepository policies = mock(CraPolicyRepository.class);
    final CraAuditService audit = mock(CraAuditService.class);
    final CraProjectRepository projects = mock(CraProjectRepository.class);
    final CraRulesService service = new CraRulesService(policies, audit, projects);

    @Test void calendarMonthsAreValidatedWithoutAnOpeningState() {
        assertEquals(YearMonth.now(), service.period(YearMonth.now().getYear(), YearMonth.now().getMonthValue()));
        assertEquals(31, service.calendar(UUID.randomUUID(), YearMonth.of(2026, 1)).days().size());
        assertThrows(BadRequestException.class, () -> service.period(2026, 13));
        assertThrows(BadRequestException.class, () -> service.period(1999, 12));
    }
    @Test void holidaysAndSelfApprovalRemainConfigurableWithoutADeadline() {
        var request = new CraOperationsDto.Policy(true, Set.of(LocalDate.of(2026, 1, 1)));
        assertEquals(request, service.savePolicy(request));
        var policy = org.mockito.ArgumentCaptor.forClass(CraPolicy.class); verify(policies).save(policy.capture());
        assertTrue(policy.getValue().isAllowSelfApproval());
        assertEquals(request.holidays(), policy.getValue().getHolidays());
        verify(audit).recordRaw(isNull(), eq("POLICY_UPDATED"), isNull(), any(), any());
    }
    @Test void historicalCalendarsDoNotNeedToBeReopened() {
        var calendar = service.calendar(UUID.randomUUID(), YearMonth.of(2025, 12));
        assertEquals(31, calendar.days().size());
        assertTrue(calendar.expectedUnits() > 0);
    }
    @Test void allCollaboratorsUseWeekdaysExcludingHolidaysAndWeekends() {
        UUID userId = UUID.randomUUID();
        var policy = new CraPolicy(); policy.setHolidays(Set.of(LocalDate.of(2026, 1, 1)));
        when(policies.findById(1)).thenReturn(Optional.of(policy));
        var calendar = service.calendar(userId, YearMonth.of(2026, 1));
        assertFalse(calendar.days().get(0).morning());
        assertTrue(calendar.days().get(1).morning());
        assertTrue(calendar.days().get(1).afternoon());
        assertFalse(calendar.days().get(2).morning());
        assertFalse(calendar.days().get(3).afternoon());
        assertEquals(21.0, calendar.expectedUnits());
        assertEquals(calendar, service.calendar(UUID.randomUUID(), YearMonth.of(2026, 1)));
    }
    @Test void wholeMonthIncludesFutureWorkButExcludesHolidaysAndClosures() {
        var owner = new User(); owner.setId(UUID.randomUUID());
        var month = YearMonth.now().plusMonths(1);
        var weekdays = month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
            .filter(date -> date.getDayOfWeek().getValue() <= 5).toList();
        var first = weekdays.get(0); var closed = weekdays.get(1);
        var policy = new CraPolicy(); policy.setHolidays(Set.of(closed));
        when(policies.findById(1)).thenReturn(Optional.of(policy));
        var calendar = service.calendar(owner.getId(), month);
        assertEquals(weekdays.size() - 1.0, calendar.expectedUnits());
        assertTrue(calendar.days().get(first.getDayOfMonth() - 1).morning());
        assertFalse(calendar.days().get(closed.getDayOfMonth() - 1).morning());
        var declaration = new CraDeclaration(); declaration.setCollaborator(owner);
        declaration.setYear(month.getYear()); declaration.setMonth(month.getMonthValue());
        assertThrows(BadRequestException.class, () -> service.requireComplete(declaration));
        for (var date : weekdays) {
            if (date.equals(first) || date.equals(closed)) continue;
            var filled = new CraDayEntry(); filled.setWorkDate(date); declaration.getEntries().add(filled);
        }
        var entry = new CraDayEntry(); entry.setWorkDate(first); entry.setDayPart(CraDayPart.MORNING);
        declaration.getEntries().add(entry);
        assertThrows(BadRequestException.class, () -> service.requireComplete(declaration));
        entry.setDayPart(CraDayPart.FULL_DAY);
        assertDoesNotThrow(() -> service.requireComplete(declaration));
    }
    @Test void datedRulesAssignAndLockSharedActivitiesOrExcludeNonWorkingDates() {
        var policy = new CraPolicy(); when(policies.findById(1)).thenReturn(Optional.of(policy));
        var rtt = new CraProject(); rtt.setId(UUID.randomUUID()); rtt.setName("RTT employeur"); rtt.setType(CraProjectType.ACCESS_ALL);
        when(projects.findById(rtt.getId())).thenReturn(Optional.of(rtt));
        var assigned = new CraOperationsDto.DateRule(LocalDate.of(2026, 6, 22), CraDateRuleType.ASSIGNED_ACTIVITY, "RTT employeur", rtt.getId(), null);
        var holiday = new CraOperationsDto.DateRule(LocalDate.of(2026, 6, 30), CraDateRuleType.PUBLIC_HOLIDAY, "Jour férié", null, null);
        var closure = new CraOperationsDto.DateRule(LocalDate.of(2026, 6, 29), CraDateRuleType.NON_WORKING, "Fermeture", null, null);
        var result = service.savePolicy(new CraOperationsDto.Policy(false, Set.of(), List.of(assigned, holiday, closure)));
        assertEquals(3, result.dateRules().size());
        var calendar = service.calendar(UUID.randomUUID(), YearMonth.of(2026, 6));
        var day = calendar.days().get(21);
        assertTrue(day.locked()); assertTrue(day.morning()); assertEquals(rtt.getId(), day.projectId());
        assertEquals("RTT employeur", day.label());
        assertTrue(calendar.days().get(29).locked()); assertFalse(calendar.days().get(29).morning());
        assertEquals(20.0, calendar.expectedUnits());
        assertFalse(calendar.days().get(22).locked());
    }
    @Test void datedRulesRejectDuplicateDatesAndClientProjects() {
        var date = LocalDate.of(2026, 6, 22);
        var closed = new CraOperationsDto.DateRule(date, CraDateRuleType.NON_WORKING, "Fermé", null, null);
        assertThrows(BadRequestException.class, () -> service.savePolicy(new CraOperationsDto.Policy(false, Set.of(), List.of(closed, closed))));
        var client = new CraProject(); client.setId(UUID.randomUUID()); client.setType(CraProjectType.CLIENT);
        when(projects.findById(client.getId())).thenReturn(Optional.of(client));
        assertThrows(BadRequestException.class, () -> service.savePolicy(new CraOperationsDto.Policy(false, Set.of(), List.of(
                new CraOperationsDto.DateRule(date, CraDateRuleType.ASSIGNED_ACTIVITY, "RTT", client.getId(), null)))));
        verify(policies, never()).save(any());
    }
        @Test void imposedActivitiesAreAutomaticProtectedAndExcludedDatesCannotBeWritten() {
        var policy = new CraPolicy(); when(policies.findById(1)).thenReturn(Optional.of(policy));
        var rtt = new CraProject(); rtt.setId(UUID.randomUUID()); rtt.setName("RTT employeur"); rtt.setType(CraProjectType.ACCESS_ALL);
        var assigned = new CraDateRule(); assigned.setType(CraDateRuleType.ASSIGNED_ACTIVITY); assigned.setProject(rtt);
        var closure = new CraDateRule(); closure.setType(CraDateRuleType.NON_WORKING);
        policy.setDateRules(Map.of(LocalDate.of(2026, 6, 22), assigned, LocalDate.of(2026, 6, 30), closure));
        var owner = new User(); owner.setId(UUID.randomUUID());
        var declaration = new CraDeclaration(); declaration.setCollaborator(owner); declaration.setYear(2026); declaration.setMonth(6);
        var normalized = service.normalizeAssignments(declaration, List.of());
        assertEquals(1, normalized.size()); assertEquals(rtt.getId(), normalized.get(0).projectId());
        assertThrows(BadRequestException.class, () -> service.normalizeAssignments(declaration, List.of(
            new com.esn.inodoc.dto.cra.CraDayAssignmentRequest(LocalDate.of(2026, 6, 22), UUID.randomUUID(), null))));
        assertThrows(BadRequestException.class, () -> service.normalizeAssignments(declaration, List.of(
            new com.esn.inodoc.dto.cra.CraDayAssignmentRequest(LocalDate.of(2026, 6, 30), rtt.getId(), null))));
        assertThrows(BadRequestException.class, () -> service.normalizeAssignments(declaration, List.of(
            new com.esn.inodoc.dto.cra.CraDayAssignmentRequest(LocalDate.of(2026, 6, 27), rtt.getId(), null))));
        assertEquals(1, service.effectiveEntries(declaration).size()); assertTrue(declaration.getEntries().isEmpty());
        service.synchronize(declaration); assertEquals(rtt, declaration.getEntries().get(0).getProject());
        }
        @Test void validatedDeclarationsKeepTheirRecordedActivitiesUntilExplicitlyUnvalidated() {
        var policy = new CraPolicy(); policy.setHolidays(Set.of(LocalDate.of(2026, 6, 22)));
        when(policies.findById(1)).thenReturn(Optional.of(policy));
        var declaration = new CraDeclaration(); declaration.setStatus(CraDeclarationStatus.VALIDATED);
        var day = new CraDayEntry(); day.setWorkDate(LocalDate.of(2026, 6, 22)); declaration.getEntries().add(day);
        assertEquals(List.of(day), service.effectiveEntries(declaration));
        service.synchronize(declaration); assertSame(day, declaration.getEntries().get(0));
        }
            @Test void retiredSharedActivitiesCanBeKeptOnAnExistingRuleButNotAssignedToANewDate() {
            var rtt = new CraProject(); rtt.setId(UUID.randomUUID()); rtt.setName("RTT employeur");
            rtt.setType(CraProjectType.ACCESS_ALL); rtt.setArchived(true);
            var rule = new CraDateRule(); rule.setType(CraDateRuleType.ASSIGNED_ACTIVITY); rule.setProject(rtt);
            var date = LocalDate.of(2026, 6, 22);
            var policy = new CraPolicy(); policy.setDateRules(Map.of(date, rule));
            when(policies.findById(1)).thenReturn(Optional.of(policy)); when(projects.findById(rtt.getId())).thenReturn(Optional.of(rtt));
            assertDoesNotThrow(() -> service.savePolicy(new CraOperationsDto.Policy(false, Set.of(), List.of(
                new CraOperationsDto.DateRule(date, CraDateRuleType.ASSIGNED_ACTIVITY, "RTT employeur", rtt.getId(), null)))));
            assertThrows(BadRequestException.class, () -> service.savePolicy(new CraOperationsDto.Policy(false, Set.of(), List.of(
                new CraOperationsDto.DateRule(date.plusDays(1), CraDateRuleType.ASSIGNED_ACTIVITY, "RTT", rtt.getId(), null)))));
            }
            @Test void aMissingRequiredHalfDayBlocksSubmission() {
        var owner = new User(); owner.setId(UUID.randomUUID());
        var declaration = new CraDeclaration(); declaration.setCollaborator(owner); declaration.setYear(2026); declaration.setMonth(1);
        var policy = new CraPolicy();
        policy.setHolidays(LocalDate.of(2026, 1, 1).datesUntil(LocalDate.of(2026, 2, 1))
            .filter(date -> !date.equals(LocalDate.of(2026, 1, 5))).collect(java.util.stream.Collectors.toSet()));
        when(policies.findById(1)).thenReturn(Optional.of(policy));
        assertThrows(BadRequestException.class, () -> service.requireComplete(declaration));
        var day = new CraDayEntry(); day.setWorkDate(LocalDate.of(2026, 1, 5)); day.setDayPart(CraDayPart.MORNING); declaration.getEntries().add(day);
        assertThrows(BadRequestException.class, () -> service.requireComplete(declaration));
        day.setDayPart(CraDayPart.FULL_DAY);
        assertDoesNotThrow(() -> service.requireComplete(declaration));
    }
}