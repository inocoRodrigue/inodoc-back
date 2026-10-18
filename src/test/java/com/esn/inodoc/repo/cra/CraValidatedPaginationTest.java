package com.esn.inodoc.repo.cra;

import com.esn.inodoc.config.JpaConfig;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraDayEntry;
import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.domain.cra.CraWorkLocation;
import com.esn.inodoc.dto.cra.CraValidatedFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
@Import({JpaConfig.class, com.esn.inodoc.service.cra.CraRulesService.class})
class CraValidatedPaginationTest {
    @Autowired CraDeclarationRepository declarations;
    @Autowired EntityManager entityManager;
    @Autowired com.esn.inodoc.repo.UserRepository users;
    @Autowired CraProjectRepository projects;
    @Autowired com.esn.inodoc.service.cra.CraRulesService rules;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.esn.inodoc.service.cra.CraAuditService audit;
    private com.esn.inodoc.domain.Role role;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CraDeclaration first;
    private CraDeclaration second;
    private CraProject atlas;
    private CraProject boreal;

    @BeforeEach
    void seed() {
        role = new com.esn.inodoc.domain.Role(); role.setName(com.esn.inodoc.domain.RoleName.INTERNAL); entityManager.persist(role);
        User alice = user("\u00c9lodie", "alice@example.test");
        User bob = user("Bob", "bob@example.test");
        User reviewer = user("Finance", "finance@example.test");
        atlas = project("Atlas");
        boreal = project("Bor\u00e9al");
        CraProject hidden = project("Hidden");
        first = declaration(alice, reviewer, 2025, 12, CraDeclarationStatus.VALIDATED);
        first.setDeclaredAt(Instant.parse("2025-12-31T12:00:00Z"));
        first.setValidatedAt(Instant.parse("2026-01-05T23:30:00Z"));
        day(first, 1, atlas, CraWorkLocation.REMOTE);
        day(first, 2, boreal, CraWorkLocation.CLIENT_SITE);
        day(first, 3, boreal, null);
        second = declaration(bob, reviewer, 2026, 1, CraDeclarationStatus.VALIDATED);
        second.setDeclaredAt(Instant.parse("2026-01-02T12:00:00Z"));
        second.setValidatedAt(Instant.parse("2026-01-10T12:00:00Z"));
        day(second, 2, atlas, CraWorkLocation.CLIENT_SITE);
        day(declaration(alice, null, 2026, 1, CraDeclarationStatus.DECLARED), 1, hidden, null);
        declaration(bob, null, 2025, 12, CraDeclarationStatus.PENDING);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void pagesAreBoundedDisjointAndOnlyPageIdsAreHydrated() {
        var request = PageRequest.of(0, 1, Sort.by(Sort.Order.desc("year"), Sort.Order.desc("month"), Sort.Order.asc("id")));
        var page = declarations.findAll(CraDeclarationSpecifications.validated(CraValidatedFilter.empty()), request);
        var next = declarations.findAll(CraDeclarationSpecifications.validated(CraValidatedFilter.empty()), request.next());
        assertEquals(2, page.getTotalElements());
        assertEquals(List.of(second.getId()), page.getContent().stream().map(CraDeclaration::getId).toList());
        assertEquals(List.of(first.getId()), next.getContent().stream().map(CraDeclaration::getId).toList());
        var details = declarations.findWithDetailsByIdIn(List.of(first.getId()));
        assertEquals(1, details.size());
        assertEquals(3, details.get(0).getEntries().size());
    }

    @Test
    void everyFilterIsEvaluatedInTheDatabaseBeforePagination() {
        var cases = List.of(
                Map.entry(Map.<String, Object>of("year", 2025), first.getId()),
                Map.entry(Map.<String, Object>of("month", 1), second.getId()),
                Map.entry(Map.<String, Object>of("collaboratorId", first.getCollaborator().getId()), first.getId()),
                Map.entry(Map.<String, Object>of("search", "ELODIE alice"), first.getId()),
                Map.entry(Map.<String, Object>of("search", "boreal"), first.getId()),
                Map.entry(Map.<String, Object>of("projectId", boreal.getId()), first.getId()),
                Map.entry(Map.<String, Object>of("workLocation", "REMOTE"), first.getId()),
                Map.entry(Map.<String, Object>of("workLocation", "UNSPECIFIED"), first.getId()),
                Map.entry(Map.<String, Object>of("declaredFrom", "2026-01-02"), second.getId()),
                Map.entry(Map.<String, Object>of("declaredTo", "2025-12-31"), first.getId()),
                Map.entry(Map.<String, Object>of("validatedFrom", "2026-01-10"), second.getId()),
                Map.entry(Map.<String, Object>of("validatedTo", "2026-01-05"), first.getId()),
                Map.entry(Map.<String, Object>of("minDays", 2), first.getId()),
                Map.entry(Map.<String, Object>of("maxDays", 1), second.getId()));
        for (var sample : cases) {
            var result = declarations.findAll(CraDeclarationSpecifications.validated(filter(sample.getKey())), PageRequest.of(0, 1));
            assertEquals(1, result.getTotalElements(), sample.getKey().toString());
            assertEquals(sample.getValue(), result.getContent().get(0).getId(), sample.getKey().toString());
        }
        assertTrue(declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of("search", "%")))).isEmpty());
    }

    @Test
    void combinedProjectAndLocationMustMatchTheSameDay() {
        var result = declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of(
                "projectId", atlas.getId(), "workLocation", "CLIENT_SITE", "validator", "Finance Test"))));
        assertEquals(List.of(second.getId()), result.stream().map(CraDeclaration::getId).toList());
    }

    @Test
    void dateBoundsAreInclusiveInTheSelectedTimeZone() {
        var values = Map.<String, Object>of("validatedFrom", "2026-01-06", "validatedTo", "2026-01-06", "timeZone", "Europe/Paris");
        var result = declarations.findAll(CraDeclarationSpecifications.validated(filter(values)));
        assertEquals(List.of(first.getId()), result.stream().map(CraDeclaration::getId).toList());
        assertTrue(declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of(
                "validatedFrom", "2026-01-06", "validatedTo", "2026-01-06")))).isEmpty());
    }

    @Test
    void filterOptionsCoverAllValidatedPagesAndExcludeOtherStatuses() {
        var status = CraDeclarationStatus.VALIDATED;
        assertEquals(List.of(2026, 2025), declarations.findYearsByStatus(status));
        assertEquals(2, declarations.findCollaboratorsByStatus(status).size());
        assertEquals(List.of("Finance Test"), declarations.findValidatorsByStatus(status));
        assertEquals(2, declarations.findProjectsByStatus(status).size());
    }

    @Test
    void invalidRangesLocationsAndTimeZonesAreRejected() {
        for (var values : List.of(Map.<String, Object>of("declaredFrom", "2026-02-01", "declaredTo", "2026-01-01"),
                Map.<String, Object>of("validatedFrom", "2026-02-01", "validatedTo", "2026-01-01"),
                Map.<String, Object>of("minDays", 4, "maxDays", 2), Map.<String, Object>of("workLocation", "INVALID"),
                Map.<String, Object>of("timeZone", "INVALID"))) {
            assertThrows(BadRequestException.class, () -> CraDeclarationSpecifications.validated(filter(values)));
        }
    }

    @Test
    void afternoonOnlyProjectsAndHalfDayQuantitiesAreSearchable() {
        var project = project("AfternoonOnly");
        var managed = entityManager.find(CraDeclaration.class, first.getId());
        managed.getEntries().get(0).setAfternoonProject(project);
        managed.getEntries().get(0).setAfternoonWorkLocation(CraWorkLocation.INOCO_OFFICE);
        var other = entityManager.find(CraDeclaration.class, second.getId());
        other.getEntries().get(0).setDayPart(com.esn.inodoc.domain.cra.CraDayPart.MORNING);
        entityManager.flush(); entityManager.clear();
        assertEquals(1, declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of("projectId", project.getId(), "workLocation", "INOCO_OFFICE")))).size());
        assertEquals(1, declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of("search", "afternoononly")))).size());
        assertEquals(List.of(second.getId()), declarations.findAll(CraDeclarationSpecifications.validated(filter(Map.of("maxDays", 0.5)))).stream().map(CraDeclaration::getId).toList());
        assertEquals(1, declarations.findAfternoonProjectsByStatus(CraDeclarationStatus.VALIDATED).size());
    }

    @Test
    void monitorFiltersMissingDraftAndSubmittedAccountsBeforePagination() {
        var period = java.time.YearMonth.of(2026, 1);
        for (var state : List.of("MISSING", "PENDING", "DECLARED", "VALIDATED")) {
            var result = users.findAll(com.esn.inodoc.service.cra.CraReportingService.criteria(period, state, ""), PageRequest.of(0, 1));
            assertEquals(state.equals("PENDING") ? 0 : 1, result.getTotalElements(), state);
        }
        assertEquals(1, users.findAll(com.esn.inodoc.service.cra.CraReportingService.criteria(period, "", "bob@example.test"), PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    void deletedProjectsDisappearFromChoicesButRemainInHistoricalDeclarations() {
        var shared = project("Congés"); shared.setType(CraProjectType.ACCESS_ALL); shared.setSystemDefined(true); shared.setArchived(true);
        var historical = entityManager.find(CraDeclaration.class, first.getId());
        historical.getEntries().get(0).setProject(shared);
        var client = entityManager.find(CraProject.class, atlas.getId()); client.setArchived(true);
        client.getCollaborators().add(entityManager.find(User.class, historical.getCollaborator().getId()));
        entityManager.flush(); entityManager.clear();

        var removed = java.util.Set.of(shared.getId(), client.getId());
        assertTrue(projects.findAllByOrderByTypeAscNameAsc().stream().noneMatch(item -> removed.contains(item.getId())));
        assertTrue(projects.findAvailableForUser(first.getCollaborator().getId(), CraProjectType.CLIENT).stream().noneMatch(item -> removed.contains(item.getId())));
        assertTrue(projects.findByCode(shared.getCode()).orElseThrow().isArchived());
        assertEquals(shared.getId(), declarations.findWithDetailsByIdIn(List.of(first.getId())).get(0).getEntries().get(0).getProject().getId());
    }

        @Test
        void dateRulesPersistAndApplyWithoutChangingValidatedHistory() {
        var rtt = project("RTT employeur"); rtt.setType(CraProjectType.ACCESS_ALL);
        var assignedDate = LocalDate.of(2026, 6, 22); var closedDate = LocalDate.of(2026, 6, 30);
        rules.savePolicy(new com.esn.inodoc.dto.cra.CraOperationsDto.Policy(false, java.util.Set.of(), List.of(
            new com.esn.inodoc.dto.cra.CraOperationsDto.DateRule(assignedDate,
                com.esn.inodoc.domain.cra.CraDateRuleType.ASSIGNED_ACTIVITY, "RTT employeur", rtt.getId(), null),
            new com.esn.inodoc.dto.cra.CraOperationsDto.DateRule(closedDate,
                com.esn.inodoc.domain.cra.CraDateRuleType.PUBLIC_HOLIDAY, "Jour férié", null, null))));
        entityManager.flush(); entityManager.clear();
        assertEquals(2, rules.policyDto().dateRules().size());
        var owner = entityManager.find(User.class, first.getCollaborator().getId());
        var draft = declaration(owner, null, 2026, 6, CraDeclarationStatus.PENDING);
        var effective = rules.effectiveEntries(draft);
        assertEquals(1, effective.size()); assertEquals(rtt.getId(), effective.get(0).getProject().getId());
        assertTrue(draft.getEntries().isEmpty());
        rules.synchronize(draft); declarations.saveAndFlush(draft);
        UUID id = draft.getId(); entityManager.clear();
        var saved = declarations.findWithDetailsByIdIn(List.of(id)).get(0);
        assertEquals(assignedDate, saved.getEntries().get(0).getWorkDate());
        assertEquals("RTT employeur", saved.getEntries().get(0).getProject().getName());
        saved.setStatus(CraDeclarationStatus.VALIDATED); declarations.saveAndFlush(saved);
        rules.savePolicy(new com.esn.inodoc.dto.cra.CraOperationsDto.Policy(false, java.util.Set.of(), List.of(
            new com.esn.inodoc.dto.cra.CraOperationsDto.DateRule(assignedDate,
                com.esn.inodoc.domain.cra.CraDateRuleType.NON_WORKING, "Fermé", null, null))));
        entityManager.flush(); entityManager.clear();
        var history = declarations.findWithDetailsByIdIn(List.of(id)).get(0);
        assertEquals(rtt.getId(), rules.effectiveEntries(history).get(0).getProject().getId());
        assertFalse(rules.calendar(owner.getId(), java.time.YearMonth.of(2026, 6)).days().get(21).morning());
        }

        private CraValidatedFilter filter(Map<String, Object> values) {
        var fields = new HashMap<String, Object>();
        fields.put("timeZone", "UTC");
        fields.putAll(values);
        return mapper.convertValue(fields, CraValidatedFilter.class);
    }

    private User user(String name, String email) {
        User user = new User();
        user.setFirstName(name);
        user.setLastName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRoles(java.util.Set.of(role));
        entityManager.persist(user);
        return user;
    }

    private CraProject project(String name) {
        CraProject project = new CraProject();
        project.setCode(UUID.randomUUID().toString());
        project.setName(name);
        project.setType(CraProjectType.CLIENT);
        entityManager.persist(project);
        return project;
    }

    private CraDeclaration declaration(User owner, User reviewer, int year, int month, CraDeclarationStatus status) {
        CraDeclaration declaration = new CraDeclaration();
        declaration.setCollaborator(owner);
        declaration.setYear(year);
        declaration.setMonth(month);
        declaration.setStatus(status);
        declaration.setValidatedBy(reviewer);
        entityManager.persist(declaration);
        return declaration;
    }

    private void day(CraDeclaration declaration, int date, CraProject project, CraWorkLocation location) {
        CraDayEntry day = new CraDayEntry();
        day.setDeclaration(declaration);
        day.setWorkDate(LocalDate.of(declaration.getYear(), declaration.getMonth(), date));
        day.setProject(project);
        day.setWorkLocation(location);
        declaration.getEntries().add(day);
        entityManager.persist(day);
    }
}