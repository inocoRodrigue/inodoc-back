package com.esn.inodoc.config;

import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.service.ProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock UserRepository users;
    @Mock ProfileRepository profiles;
    @Mock RoleRepository roles;
    @Mock ProvisioningService provisioningService;
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
    }

    @Test
    void existingRoleCatalogIsCheckedForNewRolesOnStartup() {
        when(roles.count()).thenReturn(1L);
        when(properties.getAdmin().getEmail()).thenReturn("");

        initializer.run(new DefaultApplicationArguments());

        verify(provisioningService).createDefaultRoles();
    }
}