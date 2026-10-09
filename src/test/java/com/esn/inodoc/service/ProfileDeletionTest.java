package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileDeletionTest {
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProfileService service = new ProfileService(profiles, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRoles(Set.of(role));
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Profile profile(ProfileStatus status) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName("Ext");
        profile.setLastName("Candidate");
        profile.setStatus(status);
        when(profiles.findById(profile.getId())).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void externalProfileWithoutAccountIsDeleted() {
        signInAs(RoleName.BUSINESS_MANAGER);
        Profile profile = profile(ProfileStatus.EXTERNAL);
        when(users.findByProfileId(profile.getId())).thenReturn(Optional.empty());
        service.delete(profile.getId());
        verify(profiles).delete(profile);
    }

    @Test
    void internalProfileIsRefused() {
        signInAs(RoleName.ADMIN);
        Profile profile = profile(ProfileStatus.INTERNAL);
        when(users.findByProfileId(profile.getId())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.delete(profile.getId()));
        verify(profiles, never()).delete(any(Profile.class));
    }

    @Test
    void externalProfileStillLinkedToAnAccountIsRefused() {
        signInAs(RoleName.ADMIN);
        Profile profile = profile(ProfileStatus.EXTERNAL);
        when(users.findByProfileId(profile.getId())).thenReturn(Optional.of(new User()));
        assertThrows(BadRequestException.class, () -> service.delete(profile.getId()));
        verify(profiles, never()).delete(any(Profile.class));
    }

    @Test
    void nonManagerCannotDelete() {
        signInAs(RoleName.INTERNAL);
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> service.delete(id));
        verify(profiles, never()).delete(any(Profile.class));
        verify(profiles, never()).findById(any());
    }
}
