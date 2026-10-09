package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BusinessManagerDirectoryService {
    private final UserRepository userRepository;

    public BusinessManagerDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<BusinessManagerDto> listForCurrentInternalUser() {
        requireInternalUserId();
        return userRepository.findEnabledWithRole(RoleName.BUSINESS_MANAGER).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public User requireActiveBusinessManager(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .filter(candidate -> hasRole(candidate, RoleName.BUSINESS_MANAGER))
                .orElseThrow(() -> new NotFoundException("Ingénieur d’affaires introuvable."));
        return user;
    }

    public UUID requireInternalUserId() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.getAuthorityNames().contains("ROLE_INTERNAL")) {
            throw new AccessDeniedException("Cette action est réservée aux collaborateurs internes.");
        }
        return principal.getUserId();
    }

    public UUID requireBusinessManagerId() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.getAuthorityNames().contains("ROLE_BUSINESS_MANAGER")) {
            throw new AccessDeniedException("Cette ressource ne vous a pas été partagée.");
        }
        return principal.getUserId();
    }

    private BusinessManagerDto toDto(User user) {
        return new BusinessManagerDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }

    private boolean hasRole(User user, RoleName roleName) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == roleName);
    }
}