package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.dto.user.UpdateUserRequest;
import com.esn.inodoc.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAdministrationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final UserDataCleanupRepository cleanup = mock(UserDataCleanupRepository.class);
    private final UserService service = new UserService(users, roles, profiles, mock(PasswordEncoder.class), cleanup);
    private User user;
    private Role admin;
    private Role internal;

    @BeforeEach
    void setup() {
        admin = new Role();
        admin.setName(RoleName.ADMIN);
        internal = new Role();
        internal.setName(RoleName.INTERNAL);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRoles(Set.of(admin));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(roles.lockByName(RoleName.ADMIN)).thenReturn(Optional.of(admin));
        when(roles.findByName(RoleName.INTERNAL)).thenReturn(Optional.of(internal));
        when(users.countEnabledByRole(RoleName.ADMIN)).thenReturn(1L);
    }

    @Test
    void lastActiveAdminCannotBeDemotedDisabledOrDeleted() {
        assertThrows(ConflictException.class, () -> service.updateRole(user.getId(), "INTERNAL"));
        assertThrows(ConflictException.class,
                () -> service.update(user.getId(), new UpdateUserRequest(null, null, false, null)));
        assertThrows(ConflictException.class, () -> service.delete(user.getId()));
        assertTrue(user.isEnabled());
        assertEquals(Set.of(admin), user.getRoles());
        verify(users, never()).delete(any());
        verifyNoInteractions(cleanup);
    }

    @Test
    void invalidUnknownMissingAndEmptyRolesAreBadRequests() {
        assertThrows(BadRequestException.class, () -> service.updateRole(user.getId(), "SUPERUSER"));
        assertThrows(BadRequestException.class, () -> service.updateRole(user.getId(), null));
        assertThrows(BadRequestException.class, () -> service.updateRole(user.getId(), "EXTERNAL"));
        assertThrows(BadRequestException.class,
                () -> service.update(user.getId(), new UpdateUserRequest(null, null, null, Set.of())));
    }

    @Test
    void singleRoleReplacesExistingRolesForOneUser() {
        user.setRoles(Set.of(admin, internal));
        when(users.countEnabledByRole(RoleName.ADMIN)).thenReturn(2L);
        var result = service.updateRole(user.getId(), "INTERNAL");
        assertEquals(Set.of("INTERNAL"), result.roles());
        var order = inOrder(roles, users);
        order.verify(roles).lockByName(RoleName.ADMIN);
        order.verify(users).findById(user.getId());
        order.verify(users).countEnabledByRole(RoleName.ADMIN);
        order.verify(users).save(user);
    }

    @Test
    void userCanHoldMultipleRolesAndLastAdminProtectionStillApplies() {
        var financeManager = new Role();
        financeManager.setName(RoleName.FINANCE_MANAGER);
        when(roles.findByName(RoleName.ADMIN)).thenReturn(Optional.of(admin));
        when(roles.findByName(RoleName.FINANCE_MANAGER)).thenReturn(Optional.of(financeManager));

        var updated = service.update(user.getId(), new UpdateUserRequest(
                null, null, null, Set.of(RoleName.ADMIN, RoleName.FINANCE_MANAGER)));

        assertEquals(Set.of("ADMIN", "FINANCE_MANAGER"), updated.roles());
        assertThrows(ConflictException.class, () -> service.update(user.getId(), new UpdateUserRequest(
                null, null, null, Set.of(RoleName.FINANCE_MANAGER))));
    }

    @Test
    void nonAdminAndDisabledAdminCanBeRemovedWithoutLockout() {
        user.setEnabled(false);
        service.delete(user.getId());
        verify(cleanup).deleteAssociatedData(user.getId(), null);
        verify(users).delete(user);
        verify(users, never()).countEnabledByRole(any());
    }

    @Test
    void profileIsDetachedAndDependenciesDeletedBeforeAccountAndProfile() {
        user.setRoles(Set.of(internal));
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        user.setProfile(profile);
        service.delete(user.getId());
        assertNull(user.getProfile());
        var order = inOrder(cleanup, users, profiles);
        order.verify(cleanup).deleteAssociatedData(user.getId(), profile.getId());
        order.verify(users).saveAndFlush(user);
        order.verify(users).delete(user);
        order.verify(users).flush();
        order.verify(profiles).delete(profile);
        order.verify(profiles).flush();
    }

    @Test
    void personalSignatureIsSavedPreservedWhenAbsentAndClearedByEmptyText() {
        var manager = new Role();
        manager.setName(RoleName.BUSINESS_MANAGER);
        user.setRoles(Set.of(manager));
        var principal = com.esn.inodoc.security.UserPrincipal.from(user);
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        try {
            var saved = service.updateCurrent(new com.esn.inodoc.dto.user.SelfUpdateRequest(
                    null, null, "My signature"));
            assertEquals("My signature", saved.signature());
            var unchanged = service.updateCurrent(new com.esn.inodoc.dto.user.SelfUpdateRequest(
                    null, null, null));
            assertEquals("My signature", unchanged.signature());
            var cleared = service.updateCurrent(new com.esn.inodoc.dto.user.SelfUpdateRequest(
                    null, null, ""));
            assertEquals("", cleared.signature());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void financeManagerReceivesOnlyItsRoleAuthorityAndCanUseSignature() {
        var financeManager = new Role();
        financeManager.setName(RoleName.FINANCE_MANAGER);
        user.setRoles(Set.of(financeManager));

        assertTrue(user.canUseMailSignature());
        assertEquals(Set.of("ROLE_FINANCE_MANAGER"),
                com.esn.inodoc.security.UserPrincipal.from(user).getAuthorityNames());
    }

    @Test
    void signatureIsRejectedForNonBusinessManagersButIdentityRemainsEditable() {
        for (RoleName name : new RoleName[]{RoleName.INTERNAL, RoleName.ADMIN}) {
            var role = new Role();
            role.setName(name);
            user.setRoles(Set.of(role));
            user.setSignature("Legacy signature");
            var principal = com.esn.inodoc.security.UserPrincipal.from(user);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities()));
            try {
                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                        () -> service.updateCurrent(new com.esn.inodoc.dto.user.SelfUpdateRequest(
                                null, null, "Unauthorized")));
                var result = service.updateCurrent(new com.esn.inodoc.dto.user.SelfUpdateRequest(
                        "Updated", null, null));
                assertEquals("Updated", result.firstName());
                assertNull(result.signature());
                assertEquals("Legacy signature", user.getSignature());
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }
    }
}
