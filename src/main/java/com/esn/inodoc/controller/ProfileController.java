package com.esn.inodoc.controller;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.dto.profile.ProfileDto;
import com.esn.inodoc.dto.profile.ProfileSummaryDto;
import com.esn.inodoc.dto.profile.ProfileUpsertRequest;
import com.esn.inodoc.service.ProfileService;
import com.esn.inodoc.utils.ProfileSpecifications.ProfileSearchCriteria;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
    public Page<ProfileSummaryDto> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) List<String> languages,
            @RequestParam(required = false) AvailabilityStatus availability,
            @RequestParam(required = false) ProfileStatus status,
            @RequestParam(required = false) Integer minYearsOfExperience,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableFrom,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProfileSearchCriteria criteria = new ProfileSearchCriteria(
                query, skill, technology, location, languages, availability, status,
                minYearsOfExperience, availableFrom);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by("lastName").ascending());
        return profileService.search(criteria, pageable);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ProfileDto myProfile() {
        return profileService.getMyProfile();
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ProfileDto updateMyProfile(@Valid @RequestBody ProfileUpsertRequest request) {
        return profileService.updateMyProfile(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ProfileDto get(@PathVariable UUID id) {
        return profileService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
    public ResponseEntity<ProfileDto> create(@Valid @RequestBody ProfileUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ProfileDto update(@PathVariable UUID id, @Valid @RequestBody ProfileUpsertRequest request) {
        return profileService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        profileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
