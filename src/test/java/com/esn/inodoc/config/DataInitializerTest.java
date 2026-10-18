package com.esn.inodoc.config;

import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.CraDeclarationRepository;
import com.esn.inodoc.repo.cra.CraProjectRepository;
import com.esn.inodoc.service.ProvisioningService;
import com.esn.inodoc.service.cra.CraProjectService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock UserRepository users;
    @Mock ProfileRepository profiles;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ProvisioningService provisioningService;
    @Mock CraProjectService craProjectService;
    @Mock CraDeclarationRepository craDeclarations;
    @Mock CraProjectRepository craProjects;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) AppProperties properties;
    @InjectMocks DataInitializer initializer;

    @Test
    void demoProfileContainsTwoIndependentVersionsWithDifferences() {
        when(properties.getDemo().isEnabled()).thenReturn(true);
        when(roles.count()).thenReturn(1L);
        when(properties.getAdmin().getEmail()).thenReturn("");
        initializer.run(new DefaultApplicationArguments());

        var captured = org.mockito.ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(captured.capture());
        Profile profile = captured.getValue();
        assertEquals("Alex", profile.getFirstName());
        assertEquals("Exemple", profile.getLastName());
        assertEquals("alex.exemple@example.com", profile.getEmail());
        assertNull(profile.getPhone());
        assertNull(profile.getAddress());
        assertEquals(2, profile.getDossiers().size());
        var master = profile.getDossiers().get(0);
        var comparison = profile.getDossiers().get(1);
        assertTrue(master.isMaster());
        assertFalse(comparison.isMaster());
        assertNotEquals(master.getLabel(), comparison.getLabel());
        assertNotEquals(master.getHeadline(), comparison.getHeadline());
        assertEquals(9, master.getYearsOfExperience());
        assertEquals(10, comparison.getYearsOfExperience());
        assertNotEquals(master.getSkillGroups().get(4).getContent(), comparison.getSkillGroups().get(4).getContent());
        assertEquals(master.getExperiences().size(), comparison.getExperiences().size());
        assertEquals(master.getEducations().size(), comparison.getEducations().size());
        assertEquals(master.getCertifications().size(), comparison.getCertifications().size());
        assertEquals(master.getReferences().size(), comparison.getReferences().size());
        assertEquals(master.getTestResults().size(), comparison.getTestResults().size());
        assertNotSame(master.getExperiences().get(0), comparison.getExperiences().get(0));
        assertNotSame(master.getExperiences().get(0).getProjects().get(0), comparison.getExperiences().get(0).getProjects().get(0));
        assertSame(profile, comparison.getProfile());
        assertSame(comparison, comparison.getSkillGroups().get(0).getDossier());
        assertSame(comparison.getExperiences().get(0), comparison.getExperiences().get(0).getProjects().get(0).getExperience());
    }

    @Test
    void productionDoesNotDeleteOrSeedDemoProfiles() {
        when(roles.count()).thenReturn(1L);
        when(properties.getAdmin().getEmail()).thenReturn("");
        initializer.run(new DefaultApplicationArguments());
        verify(users, never()).deleteAll();
        verify(profiles, never()).deleteAll();
        verify(profiles, never()).save(any());
        verify(craDeclarations, never()).save(any(CraDeclaration.class));
    }

    @Test
    void existingRoleCatalogIsCheckedForNewRolesOnStartup() {
        when(roles.count()).thenReturn(1L);
        when(properties.getAdmin().getEmail()).thenReturn("");

        initializer.run(new DefaultApplicationArguments());

        verify(provisioningService).createDefaultRoles();
        verify(craProjectService).ensureDefaultProjects();
    }

    @Test
    void demoModeSeedsFourRecentAndTenOlderCraMonthsForConfiguredAdmin() {
        when(properties.getDemo().isEnabled()).thenReturn(true);
        when(roles.count()).thenReturn(1L);
        when(properties.getAdmin().getEmail()).thenReturn("admin@example.test");
        when(properties.getAdmin().getPassword()).thenReturn("demo-password");
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(role(RoleName.ADMIN)));
        when(users.existsByEmailIgnoreCase("admin@example.test")).thenReturn(false);
        User admin = new User();
        admin.setId(java.util.UUID.randomUUID());
        admin.setEmail("admin@example.test");
        admin.setRoles(Set.of(role(RoleName.ADMIN)));
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(admin));
        CraProject sharedProject = new CraProject();
        sharedProject.setId(java.util.UUID.randomUUID());
        sharedProject.setCode("PAID_LEAVE");
        sharedProject.setName("Congés payés");
        sharedProject.setType(CraProjectType.ACCESS_ALL);
        sharedProject.setSystemDefined(true);
        when(craProjects.findAllByOrderByTypeAscNameAsc()).thenReturn(List.of(sharedProject));
        when(craDeclarations.save(any(CraDeclaration.class))).thenAnswer(call -> call.getArgument(0));

        initializer.run(new DefaultApplicationArguments());

        var captor = org.mockito.ArgumentCaptor.forClass(CraDeclaration.class);
        verify(craDeclarations, times(14)).save(captor.capture());
        List<CraDeclaration> declarations = captor.getAllValues();
        YearMonth current = YearMonth.now();
        assertEquals(14, declarations.stream().map(d -> d.getYear() + "-" + d.getMonth()).distinct().count());
        Set<Integer> olderYears = new java.util.HashSet<>();
        for (int monthsAgo : java.util.stream.IntStream.concat(
                java.util.stream.IntStream.rangeClosed(1, 4),
            java.util.stream.IntStream.of(12, 13, 14, 15, 16, 24, 25, 26, 27, 28)).toArray()) {
            YearMonth period = current.minusMonths(monthsAgo);
            if (monthsAgo >= 12) olderYears.add(period.getYear());
            CraDeclaration declaration = declarations.stream()
                    .filter(d -> d.getYear() == period.getYear() && d.getMonth() == period.getMonthValue())
                    .findFirst().orElseThrow();
            long weekdays = java.util.stream.IntStream.rangeClosed(1, period.lengthOfMonth())
                    .mapToObj(period::atDay)
                    .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
                            && date.getDayOfWeek() != DayOfWeek.SUNDAY)
                    .count();
            assertEquals(admin.getId(), declaration.getCollaborator().getId());
            assertEquals(CraDeclarationStatus.DECLARED, declaration.getStatus());
            assertEquals(weekdays, declaration.getEntries().size());
            assertTrue(declaration.getEntries().stream().allMatch(entry ->
                    entry.getProject().getType() == CraProjectType.ACCESS_ALL));
        }
            assertTrue(olderYears.size() >= 2);
    }

    private Role role(RoleName name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}