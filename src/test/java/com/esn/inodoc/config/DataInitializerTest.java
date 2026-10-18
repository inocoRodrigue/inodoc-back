package com.esn.inodoc.config;

import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.CompanySettingsRepository;
import com.esn.inodoc.repo.SkillCategoryRepository;
import com.esn.inodoc.repo.TechSkillRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock SkillCategoryRepository categories;
    @Mock TechSkillRepository skills;
    @Mock CompanySettingsRepository company;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ProvisioningService provisioningService;
    @Mock CraProjectService craProjectService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) AppProperties properties;
        @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
        @Mock CareerDataInitializer careerDataInitializer;
    @InjectMocks DataInitializer initializer;

        @Test
        void nonEmptyDatabaseWithExistingAdminDoesNotInitializeAnyData() {
        when(jdbc.execute(org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ConnectionCallback<Boolean>>any()))
            .thenReturn(false);
        when(properties.getAdmin().getEmail()).thenReturn(" Admin@Example.test ");
        when(users.existsByEmailIgnoreCase("admin@example.test")).thenReturn(true);
        initializer.run(new DefaultApplicationArguments());
        verify(users).existsByEmailIgnoreCase("admin@example.test");
        verifyNoMoreInteractions(users);
        verifyNoInteractions(roles, categories, skills, company, provisioningService, craProjectService,
            passwordEncoder, careerDataInitializer);
        verify(properties.getAdmin(), never()).getPassword();
        }

    @Test
    void emptyDatabaseInitializesOnlyReferenceDataAndConfiguredAdmin() {
        when(jdbc.execute(org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ConnectionCallback<Boolean>>any()))
                .thenReturn(true);
        when(properties.getAdmin().getEmail()).thenReturn(" Admin@Example.test ");
        when(properties.getAdmin().getPassword()).thenReturn("bootstrap-password");
        when(passwordEncoder.encode("bootstrap-password")).thenReturn("encoded-password");
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(role(RoleName.ADMIN)));
        initializer.run(new DefaultApplicationArguments());
        verify(provisioningService).createDefaultRoles();
        verify(craProjectService).ensureDefaultProjects();
        verify(careerDataInitializer).initialize();
        verify(company).save(any());
        verify(categories, atLeastOnce()).save(any());
        verify(skills, atLeastOnce()).save(any());
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("admin@example.test", captured.getValue().getEmail());
        assertEquals("encoded-password", captured.getValue().getPasswordHash());
        assertNull(captured.getValue().getProfile());
        assertEquals(1, captured.getValue().getRoles().size());
    }

    @Test
    void resetInitializesReferencesButDoesNotRecreateTheEnvironmentAdmin() {
        initializer.initializeAfterReset();
        verify(provisioningService).createDefaultRoles();
        verify(craProjectService).ensureDefaultProjects();
        verify(roles).flush();
        verify(careerDataInitializer).initialize();
        verifyNoInteractions(users, properties, passwordEncoder, jdbc);
    }

    @Test
    void emptyDatabaseRejectsMissingOrWeakAdminCredentials() {
        when(jdbc.execute(org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ConnectionCallback<Boolean>>any()))
                .thenReturn(true);
        when(properties.getAdmin().getEmail()).thenReturn("admin@example.test");
        when(properties.getAdmin().getPassword()).thenReturn("short");
        assertThrows(IllegalStateException.class, () -> initializer.run(new DefaultApplicationArguments()));
        verify(users, never()).save(any());
    }

    @Test
        void existingBusinessDataDoesNotPreventCreatingMissingAdminAndRole() {
        var actualJdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:initializer;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", ""));
        actualJdbc.execute("DROP ALL OBJECTS");
        actualJdbc.execute("CREATE TABLE flyway_schema_history(id INTEGER)");
        actualJdbc.execute("INSERT INTO flyway_schema_history VALUES (1)");
        actualJdbc.execute("CREATE TABLE review_forms(id INTEGER)");
        actualJdbc.execute("INSERT INTO review_forms VALUES (1)");
        var actual = new DataInitializer(users, roles, categories, skills, company, provisioningService,
                craProjectService, passwordEncoder, properties, actualJdbc, careerDataInitializer);
        when(properties.getAdmin().getEmail()).thenReturn("admin@example.test");
        when(properties.getAdmin().getPassword()).thenReturn("bootstrap-password");
        when(passwordEncoder.encode("bootstrap-password")).thenReturn("encoded-password");
        when(roles.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        actual.run(new DefaultApplicationArguments());
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("admin@example.test", captured.getValue().getEmail());
        assertEquals("encoded-password", captured.getValue().getPasswordHash());
        assertEquals(RoleName.ADMIN, captured.getValue().getRoles().iterator().next().getName());
        var createdRole = org.mockito.ArgumentCaptor.forClass(Role.class);
        verify(roles).save(createdRole.capture());
        assertEquals(RoleName.ADMIN, createdRole.getValue().getName());
        verifyNoInteractions(categories, skills, company, provisioningService, craProjectService, careerDataInitializer);
        assertEquals(1, actualJdbc.queryForObject("SELECT COUNT(*) FROM review_forms", Integer.class));
    }

        @Test
        void missingAdminIsCreatedWithoutRecreatingAnExistingAdminRole() {
        when(jdbc.execute(org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ConnectionCallback<Boolean>>any()))
            .thenReturn(false);
        when(properties.getAdmin().getEmail()).thenReturn("admin@example.test");
        when(properties.getAdmin().getPassword()).thenReturn("bootstrap-password");
        when(passwordEncoder.encode("bootstrap-password")).thenReturn("encoded-password");
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(role(RoleName.ADMIN)));
        initializer.run(new DefaultApplicationArguments());
        verify(users).save(any(User.class));
        verify(roles, never()).save(any());
        verifyNoInteractions(categories, skills, company, provisioningService, craProjectService, careerDataInitializer);
        }

    private Role role(RoleName name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}