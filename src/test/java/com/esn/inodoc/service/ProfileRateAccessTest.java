package com.esn.inodoc.service;

import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.dto.profile.ProfileUpsertRequest;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileRateAccessTest {
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanySettingsService settings = mock(CompanySettingsService.class);
    private final ProfileService service = new ProfileService(profiles, users, settings);
    private final Profile profile = new Profile();
    private final User user = new User();

    ProfileRateAccessTest() {
        UUID profileId = UUID.randomUUID();
        profile.setId(profileId);
        profile.setFirstName("Alice");
        profile.setLastName("Martin");
        profile.setDailyRate(650);
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.test");
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setProfile(profile);
        Role internal = new Role();
        internal.setName(RoleName.INTERNAL);
        user.setRoles(Set.of(internal));
        when(users.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(users.findByProfileId(profileId)).thenReturn(java.util.Optional.of(user));
        when(profiles.findById(profileId)).thenReturn(java.util.Optional.of(profile));
        when(profiles.save(profile)).thenReturn(profile);
        when(settings.get()).thenReturn(new CompanyContactDto(null, null, null, null, null, 12));
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalUserCannotReadTheirDailyRate() {
        assertNull(service.getMyProfile().dailyRate());
    }

    @Test
    void internalUserCannotCreateUnlinkedProfiles() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.create(new ProfileUpsertRequest("Alice", "Martin", null, null, null, null,
                        null, null, null, 650)));
    }

    @Test
    void internalUserCannotChangeTheirDailyRateThroughOwnProfileUpdate() {
        service.updateMyProfile(new ProfileUpsertRequest("Alice", "Martin", null, null, null, null,
                null, null, null, 1));

        assertEquals(650, profile.getDailyRate());
    }

    @Test
    void businessManagerCanReadTheirDailyRate() {
        Role manager = new Role(); manager.setName(RoleName.BUSINESS_MANAGER);
        user.setRoles(Set.of(manager));
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        assertEquals(650, service.getMyProfile().dailyRate());
    }
}