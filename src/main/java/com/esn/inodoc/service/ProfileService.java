package com.esn.inodoc.service;

import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.profile.ProfileDto;
import com.esn.inodoc.dto.profile.ProfileMapper;
import com.esn.inodoc.dto.profile.ProfileSummaryDto;
import com.esn.inodoc.dto.profile.ProfileUpsertRequest;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.utils.ProfileSpecifications;
import com.esn.inodoc.utils.SecurityUtils;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.controller.error.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CompanySettingsService companySettingsService;

    public ProfileService(ProfileRepository profileRepository,
                          UserRepository userRepository,
                          CompanySettingsService companySettingsService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.companySettingsService = companySettingsService;
    }

    @Transactional(readOnly = true)
    public Page<ProfileSummaryDto> search(ProfileSpecifications.ProfileSearchCriteria criteria, Pageable pageable) {
        int staleAfterMonths = companySettingsService.get().staleDossierMonths();
        if (!canManageAll()) {
            UUID ownProfileId = currentOwnProfileId();
            if (ownProfileId == null) {
                return Page.empty(pageable);
            }
            Profile own = profileRepository.findById(ownProfileId).orElse(null);
            return own == null ? Page.empty(pageable)
                    : new PageImpl<>(List.of(ProfileMapper.toSummary(own, staleAfterMonths)), pageable, 1);
        }
        return profileRepository.findAll(ProfileSpecifications.build(criteria), pageable)
                .map(profile -> ProfileMapper.toSummary(profile, staleAfterMonths));
    }

    @Transactional(readOnly = true)
    public ProfileDto get(UUID id) {
        Profile profile = requireProfile(id);
        assertCanAccess(profile.getId());
        User account = userRepository.findByProfileId(profile.getId()).orElse(null);
        return profileDto(profile, account);
    }

    @Transactional(readOnly = true)
    public ProfileDto getMyProfile() {
        UUID ownProfileId = currentOwnProfileId();
        if (ownProfileId == null) {
            throw new NotFoundException("Aucun dossier de compétences associé à votre compte");
        }
        return profileDto(requireProfile(ownProfileId), null);
    }

    @Transactional
    public ProfileDto create(ProfileUpsertRequest request) {
        Profile profile = new Profile();
        ProfileMapper.apply(profile, request);
        if (!canManageAll()) profile.setDailyRate(null);
        // Rule: a profile is internal only if it is linked to an account.
        // On creation, no account is associated => external.
        profile.setStatus(ProfileStatus.EXTERNAL);
        profile = profileRepository.save(profile);
        return profileDto(profile, null);
    }

    @Transactional
    public ProfileDto update(UUID id, ProfileUpsertRequest request) {
        Profile profile = requireProfile(id);
        assertCanAccess(profile.getId());
        Integer existingDailyRate = profile.getDailyRate();
        ProfileMapper.apply(profile, request);
        if (!canManageAll()) profile.setDailyRate(existingDailyRate);
        User account = userRepository.findByProfileId(profile.getId()).orElse(null);
        if (account != null) {
            // The identity of a dossier linked to an internal account comes from the account.
            profile.setFirstName(account.getFirstName());
            profile.setLastName(account.getLastName());
            profile.setEmail(account.getEmail());
            profile.setStatus(ProfileStatus.INTERNAL);
        } else {
            // Rule: without an associated account, the profile is external.
            profile.setStatus(ProfileStatus.EXTERNAL);
        }
        profile = profileRepository.save(profile);
        return profileDto(profile, account);
    }

    @Transactional
    public ProfileDto updateMyProfile(ProfileUpsertRequest request) {
        UUID ownProfileId = currentOwnProfileId();
        if (ownProfileId == null) {
            throw new NotFoundException("Aucun dossier de compétences associé à votre compte");
        }
        return update(ownProfileId, request);
    }

    @Transactional
    public void delete(UUID id) {
        if (!canManageAll()) {
            throw new AccessDeniedException("Suppression non autorisée");
        }
        Profile profile = requireProfile(id);
        // An internal profile is bound to a user account: deleting it directly
        // would orphan the account. Such profiles are removed via user deletion.
        if (userRepository.findByProfileId(id).isPresent()
                || profile.getStatus() == ProfileStatus.INTERNAL) {
            throw new BadRequestException(
                    "Ce profil est interne et lié à un compte. Supprimez le compte depuis Utilisateurs.");
        }
        // Dossier versions cascade (JPA); their share links cascade at the DB level.
        profileRepository.delete(profile);
    }

    public Profile requireProfile(UUID id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Personne introuvable"));
    }

    public void assertCanAccess(UUID profileId) {
        if (canManageAll()) {
            return;
        }
        UUID ownProfileId = currentOwnProfileId();
        if (ownProfileId == null || !ownProfileId.equals(profileId)) {
            throw new AccessDeniedException("Vous ne pouvez accéder qu'à votre propre dossier");
        }
    }

    /** ADMIN or business manager: access to all profiles and version management. */
    public boolean canManageAll() {
        return SecurityUtils.getCurrentPrincipal()
                .map(UserPrincipal::getAuthorityNames)
                .map(names -> names.contains("ROLE_ADMIN") || names.contains("ROLE_BUSINESS_MANAGER")
                    || names.contains("ROLE_FINANCE_MANAGER"))
                .orElse(false);
    }

    private ProfileDto profileDto(Profile profile, User account) {
        int staleAfterMonths = companySettingsService.get().staleDossierMonths();
        ProfileDto dto = ProfileMapper.toDto(profile, account, staleAfterMonths);
        if (canManageAll()) return dto;
        return new ProfileDto(dto.id(), dto.firstName(), dto.lastName(), dto.email(), dto.phone(), dto.address(),
                dto.location(), dto.availabilityStatus(), dto.availableFrom(), dto.status(), null, dto.dossiers(),
                dto.accountUserId(), dto.accountEmail(), dto.createdAt(), dto.updatedAt(), dto.imageUrl());
    }

    private UUID currentOwnProfileId() {
        return SecurityUtils.getCurrentPrincipal()
                .flatMap(p -> userRepository.findById(p.getUserId()))
                .map(User::getProfile)
                .map(Profile::getId)
                .orElse(null);
    }

    public void createEmptyProfileForUser(User user) {
        Profile profile = new Profile();
        profile.setFirstName(user.getFirstName());
        profile.setLastName(user.getLastName());
        profile.setEmail(user.getEmail());
        profile.setStatus(ProfileStatus.INTERNAL);
        profile = profileRepository.save(profile);
        user.setProfile(profile);
        userRepository.save(user);
    }
}
