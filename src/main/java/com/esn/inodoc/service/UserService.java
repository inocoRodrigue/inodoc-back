package com.esn.inodoc.service;

import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.user.CreateUserRequest;
import com.esn.inodoc.dto.user.LinkProfileRequest;
import com.esn.inodoc.dto.user.SelfUpdateRequest;
import com.esn.inodoc.dto.user.UpdateUserRequest;
import com.esn.inodoc.dto.user.UserDto;
import com.esn.inodoc.dto.user.UserMapper;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.UserDataCleanupRepository;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.utils.SecurityUtils;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDataCleanupRepository cleanupRepository;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       UserDataCleanupRepository cleanupRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.cleanupRepository = cleanupRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAll()
                .stream().map(UserMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return UserMapper.toDto(requireUser(id));
    }

    @Transactional(readOnly = true)
    public UserDto currentUser() {
        UUID userId = SecurityUtils.requirePrincipal().getUserId();
        return UserMapper.toDto(requireUser(userId));
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un utilisateur avec cet email existe déjà");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setRoles(resolveRoles(request.roles()));

        user = userRepository.save(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        lockAdminChanges();
        User user = requireUser(id);
        Set<Role> roles = request.roles() == null ? user.getRoles() : resolveRoles(request.roles());
        boolean enabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        protectLastAdmin(user, enabled, roles);
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.roles() != null) {
            user.setRoles(roles);
        }
        user = userRepository.save(user);
        syncLinkedProfile(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public UserDto updateRole(UUID id, String requestedRole) {
        RoleName role;
        try {
            role = RoleName.valueOf(requestedRole == null ? "" : requestedRole);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Rôle invalide");
        }
        return update(id, new UpdateUserRequest(null, null, null, Set.of(role)));
    }

    @Transactional
    public UserDto updateCurrent(SelfUpdateRequest request) {
        User user = requireUser(SecurityUtils.requirePrincipal().getUserId());
        if (request.signature() != null) {
            if (!user.canUseMailSignature()) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "La signature des emails est réservée aux ingénieurs d'affaires");
            }
            user.setSignature(request.signature());
        }
        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        user = userRepository.save(user);
        syncLinkedProfile(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public UserDto linkProfile(UUID userId, UUID profileId) {
        User user = requireUser(userId);
        if (profileId == null) {
            // Unlinking: the profile is no longer linked to an account => it becomes external again.
            Profile current = user.getProfile();
            if (current != null) {
                current.setStatus(ProfileStatus.EXTERNAL);
                profileRepository.save(current);
            }
            user.setProfile(null);
            user = userRepository.save(user);
            return UserMapper.toDto(user);
        }
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Profil introuvable"));
        userRepository.findByProfileId(profileId)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ConflictException("Ce profil est déjà associé à un autre compte");
                });
        // If the account was already linked to another profile, that profile becomes external again.
        Profile previous = user.getProfile();
        if (previous != null && !previous.getId().equals(profileId)) {
            previous.setStatus(ProfileStatus.EXTERNAL);
            profileRepository.save(previous);
        }
        // Link a dossier to an internal account: the person becomes internal
        // and their identity now comes from the account.
        profile.setStatus(ProfileStatus.INTERNAL);
        user.setProfile(profile);
        user = userRepository.save(user);
        syncLinkedProfile(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public void delete(UUID id) {
        lockAdminChanges();
        User user = requireUser(id);
        protectLastAdmin(user, false, Set.of());
        Profile profile = user.getProfile();
        cleanupRepository.deleteAssociatedData(id, profile == null ? null : profile.getId());
        user.setProfile(null);
        userRepository.saveAndFlush(user);
        userRepository.delete(user);
        userRepository.flush();
        if (profile != null) {
            profileRepository.delete(profile);
            profileRepository.flush();
        }
    }

    private void lockAdminChanges() {
        // A stable, shared database lock serializes demotion, deactivation and deletion.
        roleRepository.lockByName(RoleName.ADMIN)
                .orElseThrow(() -> new ConflictException("Le rôle administrateur est introuvable"));
    }

    private void protectLastAdmin(User user, boolean enabled, Set<Role> roles) {
        boolean wasAdmin = user.isEnabled()
                && user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
        boolean remainsAdmin = enabled && roles.stream().anyMatch(r -> r.getName() == RoleName.ADMIN);
        if (wasAdmin && !remainsAdmin && userRepository.countEnabledByRole(RoleName.ADMIN) <= 1) {
            throw new ConflictException("Le dernier administrateur actif ne peut pas être supprimé, désactivé ou rétrogradé");
        }
    }

    /**
     * Propagates the account identity to the possibly associated internal
     * competence dossier: the account is the source of truth for the name.
     */
    private void syncLinkedProfile(User user) {
        Profile profile = user.getProfile();
        if (profile != null) {
            profile.setFirstName(user.getFirstName());
            profile.setLastName(user.getLastName());
            profile.setEmail(user.getEmail());
            if (user.getImageUrl() != null) {
                profile.setImageUrl(user.getImageUrl());
            }
            profileRepository.save(profile);
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
    }

    private Set<Role> resolveRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BadRequestException("Au moins un rôle valide est requis");
        }
        Set<Role> roles = new HashSet<>();
        for (RoleName name : roleNames) {
            if (name == null) {
                throw new BadRequestException("Rôle invalide");
            }
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new BadRequestException("Rôle invalide : " + name)));
        }
        return roles;
    }
}
