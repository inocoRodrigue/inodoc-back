package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.dto.cra.CraProjectRequest;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.CraProjectRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.service.CraFeatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraProjectServiceTest {
    @org.junit.jupiter.api.Test
    void clientNameIsTrimmedAndOnlyStoredForClientProjects() {
        var repository = org.mockito.Mockito.mock(com.esn.inodoc.repo.cra.CraProjectRepository.class);
        var users = org.mockito.Mockito.mock(com.esn.inodoc.repo.UserRepository.class);
        var feature = org.mockito.Mockito.mock(com.esn.inodoc.service.CraFeatureService.class);
        org.mockito.Mockito.when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
        var service = new CraProjectService(repository, users, feature);
        var client = service.createProject(new com.esn.inodoc.dto.cra.CraProjectRequest("Mission", null, com.esn.inodoc.domain.cra.CraProjectType.CLIENT, java.util.Set.of(), "  Acme  "));
        org.junit.jupiter.api.Assertions.assertEquals("Acme", client.clientName());
        var shared = service.createProject(new com.esn.inodoc.dto.cra.CraProjectRequest("Formation", null, com.esn.inodoc.domain.cra.CraProjectType.ACCESS_ALL, java.util.Set.of(), "Acme"));
        org.junit.jupiter.api.Assertions.assertNull(shared.clientName());
    }
    private final CraProjectRepository projects = mock(CraProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CraFeatureService feature = mock(CraFeatureService.class);
    private final CraProjectService service = new CraProjectService(projects, users, feature);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seedsSharedActivityCatalogOnce() {
        Map<String, CraProject> stored = new java.util.HashMap<>();
        when(projects.findByCode(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(projects.save(any(CraProject.class))).thenAnswer(call -> {
            CraProject project = call.getArgument(0);
            stored.put(project.getCode(), project);
            return project;
        });

        service.ensureDefaultProjects();
        service.ensureDefaultProjects();

        assertEquals(11, stored.size());
        assertTrue(stored.values().stream().allMatch(CraProject::isSystemDefined));
        assertTrue(stored.values().stream().allMatch(project -> project.getType() == CraProjectType.ACCESS_ALL));
        assertTrue(stored.values().stream().allMatch(project -> project.getDescription() != null
                && !project.getDescription().isBlank()));
        verify(projects, times(11)).save(any(CraProject.class));
    }

    @Test
    void preservesCustomDescriptionsWhenEnsuringSharedActivities() {
        CraProject customized = project("INTERNAL_TRAINING", "Formation interne", CraProjectType.ACCESS_ALL, true);
        customized.setDescription("Description validée par la finance");
        Map<String, CraProject> stored = new java.util.HashMap<>();
        stored.put(customized.getCode(), customized);
        when(projects.findByCode(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(projects.save(any(CraProject.class))).thenAnswer(call -> {
            CraProject saved = call.getArgument(0);
            stored.put(saved.getCode(), saved);
            return saved;
        });

        service.ensureDefaultProjects();

        assertEquals("Description validée par la finance", stored.get("INTERNAL_TRAINING").getDescription());
        assertTrue(stored.values().stream().allMatch(project -> project.getDescription() != null
                && !project.getDescription().isBlank()));
    }

    @Test
    void rejectsCollaboratorAssignmentsDuringProjectCreation() {
        UUID employeeId = UUID.randomUUID();
        when(projects.existsByNameIgnoreCaseAndType("Migration", CraProjectType.CLIENT)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.createProject(new CraProjectRequest(
                " Migration ", " Deployment support ", CraProjectType.CLIENT, Set.of(employeeId))));
    }

    @Test
    void createsProjectWithoutCollaboratorsAndAllowsMultipleAssignmentsLater() {
        CraProject project = project("CLIENT_1", "Migration", CraProjectType.CLIENT, false);
        project.setCollaborators(Set.of());
        when(projects.existsByNameIgnoreCaseAndType("Migration", CraProjectType.CLIENT)).thenReturn(false);
        when(projects.save(any(CraProject.class))).thenAnswer(call -> {
            CraProject saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        var created = service.createProject(new CraProjectRequest("Migration", null, CraProjectType.CLIENT, null));
        assertTrue(created.collaborators().isEmpty());

        User first = internalEmployee(UUID.randomUUID());
        User second = internalEmployee(UUID.randomUUID());
        second.setLastName("Dupont");
        project.setId(created.id());
        when(projects.findById(created.id())).thenReturn(Optional.of(project));
        when(users.findEnabledWithRole(RoleName.INTERNAL)).thenReturn(List.of(first, second));
        when(projects.save(any(CraProject.class))).thenAnswer(call -> call.getArgument(0));

        var updated = service.updateProject(created.id(), new CraProjectRequest(
            "Migration", "Projet récurrent", CraProjectType.CLIENT, Set.of(first.getId(), second.getId())));

        assertEquals("Projet récurrent", updated.description());
        assertEquals(2, updated.collaborators().size());

        when(projects.existsByNameIgnoreCaseAndType("Migration", CraProjectType.ACCESS_ALL)).thenReturn(false);
        var shared = service.updateProject(created.id(), new CraProjectRequest(
            "Migration", "Projet partagé", CraProjectType.ACCESS_ALL, null));
        assertEquals(CraProjectType.ACCESS_ALL, shared.type());
        assertTrue(shared.collaborators().isEmpty());
    }

    @Test
    void rejectsUsersWhoAreNotActiveInternalCollaborators() {
        UUID userId = UUID.randomUUID();
        when(projects.existsByNameIgnoreCaseAndType("Migration", CraProjectType.CLIENT)).thenReturn(false);
        when(users.findEnabledWithRole(RoleName.INTERNAL)).thenReturn(List.of());

        assertThrows(BadRequestException.class, () -> service.createProject(
                new CraProjectRequest("Migration", null, CraProjectType.CLIENT, Set.of(userId))));
    }

            @Test
            void updatesSharedActivityNameAndDescriptionWithoutChangingItsAccessType() {
            CraProject shared = project("PAID_LEAVE", "Congés payés", CraProjectType.ACCESS_ALL, true);
            when(projects.findById(shared.getId())).thenReturn(Optional.of(shared));
            when(projects.existsByNameIgnoreCaseAndType("Absence rémunérée", CraProjectType.ACCESS_ALL))
                .thenReturn(false);
            when(projects.save(any(CraProject.class))).thenAnswer(call -> call.getArgument(0));

            var updated = service.updateProject(shared.getId(), new CraProjectRequest(
                "Absence rémunérée", "Activité commune", CraProjectType.ACCESS_ALL, null));

            assertEquals("Absence rémunérée", updated.name());
            assertEquals("Activité commune", updated.description());
            assertEquals(CraProjectType.ACCESS_ALL, updated.type());
            assertTrue(updated.systemDefined());
            assertTrue(updated.collaborators().isEmpty());
            }

        @Test
        void createsAndConvertsCustomProjectsToAllAccessWithoutAssignments() {
            when(projects.existsByNameIgnoreCaseAndType("Support interne", CraProjectType.ACCESS_ALL)).thenReturn(false);
            when(projects.save(any(CraProject.class))).thenAnswer(call -> {
                CraProject saved = call.getArgument(0);
                if (saved.getId() == null) saved.setId(UUID.randomUUID());
                return saved;
            });

            var created = service.createProject(new CraProjectRequest(
                    "Support interne", null, CraProjectType.ACCESS_ALL, null));

            assertEquals(CraProjectType.ACCESS_ALL, created.type());
            assertTrue(created.collaborators().isEmpty());
            assertFalse(created.systemDefined());

                CraProject custom = project(created.code(), created.name(), CraProjectType.ACCESS_ALL, false);
                custom.setId(created.id());
                when(projects.findById(created.id())).thenReturn(Optional.of(custom));
                when(projects.existsByNameIgnoreCaseAndType("Support interne", CraProjectType.CLIENT)).thenReturn(false);

                var converted = service.updateProject(created.id(), new CraProjectRequest(
                    "Support interne", null, CraProjectType.CLIENT, null));

                assertEquals(CraProjectType.CLIENT, converted.type());
                assertTrue(converted.collaborators().isEmpty());
        }

    @Test
    void returnsOnlyProjectsAllowedByTheRepositoryForCurrentEmployee() {
        UUID userId = UUID.randomUUID();
        User employee = internalEmployee(userId);
        CraProject shared = project("PAID_LEAVE", "Congés payés", CraProjectType.ACCESS_ALL, true);
        CraProject assignedClient = project("CLIENT_1", "Migration", CraProjectType.CLIENT, false);
        assignedClient.setCollaborators(Set.of(employee));
        signIn(employee);
        when(feature.isEnabled()).thenReturn(true);
        when(projects.findAvailableForUser(userId, CraProjectType.CLIENT)).thenReturn(List.of(shared, assignedClient));

        var available = service.availableForCurrentCollaborator();

        assertEquals(List.of("Congés payés", "Migration"), available.stream().map(p -> p.name()).toList());
        verify(projects).findAvailableForUser(userId, CraProjectType.CLIENT);
    }

    @Test
    void doesNotListProjectsWhenCraIsDisabled() {
        User employee = internalEmployee(UUID.randomUUID());
        signIn(employee);
        when(feature.isEnabled()).thenReturn(false);

        assertThrows(com.esn.inodoc.controller.error.NotFoundException.class,
                service::availableForCurrentCollaborator);
    }

    @Test
    void defaultSharedActivityCanBeDeletedWithoutRemovingHistoricalReferences() {
        var shared = project("PAID_LEAVE", "Congés payés", CraProjectType.ACCESS_ALL, true);
        when(projects.findById(shared.getId())).thenReturn(Optional.of(shared));

        service.deleteCustomProject(shared.getId());
        service.deleteCustomProject(shared.getId());

        assertTrue(shared.isArchived());
        verify(projects).save(shared);
        org.mockito.Mockito.verify(projects, org.mockito.Mockito.never()).delete(any(CraProject.class));
        assertThrows(com.esn.inodoc.controller.error.NotFoundException.class, () -> service.updateProject(shared.getId(),
                new CraProjectRequest("Congés", null, CraProjectType.ACCESS_ALL, null)));
    }

    @Test
    void deletedDefaultActivitiesAreNotRecreatedOrReactivatedAtStartup() {
        var removed = project("PAID_LEAVE", "Congés payés", CraProjectType.ACCESS_ALL, true);
        removed.setArchived(true);
        Map<String, CraProject> stored = new java.util.HashMap<>(); stored.put(removed.getCode(), removed);
        when(projects.findByCode(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(projects.save(any(CraProject.class))).thenAnswer(call -> {
            CraProject saved = call.getArgument(0); stored.put(saved.getCode(), saved); return saved;
        });

        service.ensureDefaultProjects(); service.ensureDefaultProjects();

        assertTrue(stored.get("PAID_LEAVE").isArchived());
        org.junit.jupiter.api.Assertions.assertSame(removed, stored.get("PAID_LEAVE"));
        verify(projects, times(10)).save(any(CraProject.class));
    }

    private User internalEmployee(UUID id) {
        Role role = new Role();
        role.setName(RoleName.INTERNAL);
        User user = new User();
        user.setId(id);
        user.setFirstName("Aline");
        user.setLastName("Salariée");
        user.setEmail("aline@example.test");
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        return user;
    }

    private CraProject project(String code, String name, CraProjectType type, boolean systemDefined) {
        CraProject project = new CraProject();
        project.setId(UUID.randomUUID());
        project.setCode(code);
        project.setName(name);
        project.setType(type);
        project.setSystemDefined(systemDefined);
        return project;
    }

    private void signIn(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}