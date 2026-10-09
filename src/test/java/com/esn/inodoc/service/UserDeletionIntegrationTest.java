package com.esn.inodoc.service;

import com.esn.inodoc.config.JpaConfig;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.repo.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@Import({UserService.class, UserDataCleanupRepository.class, JpaConfig.class, UserDeletionIntegrationTest.Config.class})
class UserDeletionIntegrationTest {
    @TestConfiguration
    static class Config {
        @Bean PasswordEncoder passwordEncoder() { return mock(PasswordEncoder.class); }
    }

    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProfileRepository profiles;
    @Autowired EntityManager em;

    @Test
    void deletingUserRemovesProfileDossiersChildrenSharingTokensAndMailButKeepsOtherUsers() {
        Role admin = new Role();
        admin.setName(RoleName.ADMIN);
        roles.save(admin);
        Role internal = new Role();
        internal.setName(RoleName.INTERNAL);
        roles.save(internal);
        User survivor = account("admin@example.test", admin);
        Profile profile = new Profile();
        profile.setFirstName("Consultant");
        profile.setLastName("Test");
        CompetenceDossier dossier = new CompetenceDossier();
        dossier.setLabel("Version");
        profile.addDossier(dossier);
        profiles.saveAndFlush(profile);
        User target = account("target@example.test", internal);
        target.setProfile(profile);
        users.saveAndFlush(target);
        Education education = new Education();
        education.setSchool("Test school");
        education.setDossier(dossier);
        em.persist(education);
        SharedLink link = new SharedLink();
        link.setToken("target-share");
        link.setType(ShareType.CLIENT_READONLY);
        link.setDossier(dossier);
        link.setCreatedBy(survivor.getId());
        em.persist(link);
        RefreshToken token = new RefreshToken();
        token.setUser(target);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        em.persist(token);
        MailAccount mail = new MailAccount();
        mail.setEmail("mail@example.test");
        mail.setCreatedBy(target.getId());
        mail.setRefreshTokenEncrypted("encrypted");
        em.persist(mail);
        em.flush();
        UUID targetId = target.getId();
        UUID profileId = profile.getId();
        em.clear();

        service.delete(targetId);
        em.flush();
        em.clear();
        assertFalse(users.existsById(targetId));
        assertFalse(profiles.existsById(profileId));
        assertTrue(users.existsById(survivor.getId()));
        for (String entity : new String[]{"CompetenceDossier", "Education", "SharedLink", "RefreshToken", "MailAccount"}) {
            assertEquals(0L, em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(), entity);
        }
    }

    private User account(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(new HashSet<>(Set.of(role)));
        return users.saveAndFlush(user);
    }
}
