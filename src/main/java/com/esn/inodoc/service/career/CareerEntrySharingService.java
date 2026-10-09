package com.esn.inodoc.service.career;

import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.career.CareerEntry;
import com.esn.inodoc.domain.career.CareerEntryShare;
import com.esn.inodoc.dto.career.SharedCareerEntryDto;
import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.career.CareerEntryRepository;
import com.esn.inodoc.repo.career.CareerEntryShareRepository;
import com.esn.inodoc.service.BusinessManagerDirectoryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CareerEntrySharingService {
    private final CareerEntryRepository careerEntryRepository;
    private final CareerEntryShareRepository shareRepository;
    private final UserRepository userRepository;
    private final BusinessManagerDirectoryService managerDirectory;
    private final ObjectMapper mapper;

    public CareerEntrySharingService(CareerEntryRepository careerEntryRepository,
                                    CareerEntryShareRepository shareRepository,
                                    UserRepository userRepository,
                                    BusinessManagerDirectoryService managerDirectory,
                                    ObjectMapper mapper) {
        this.careerEntryRepository = careerEntryRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.managerDirectory = managerDirectory;
        this.mapper = mapper;
    }

    @Transactional
    public BusinessManagerDto share(UUID entryId, UUID managerId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        CareerEntry entry = requireOwnedEntry(entryId, ownerId);
        User manager = managerDirectory.requireActiveBusinessManager(managerId);
        if (shareRepository.existsByCareerEntryIdAndBusinessManagerId(entryId, managerId)) {
            throw new ConflictException("Cette entrée est déjà partagée avec cet ingénieur d’affaires.");
        }

        CareerEntryShare share = new CareerEntryShare();
        share.setCareerEntry(entry);
        share.setBusinessManager(manager);
        try {
            shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Cette entrée est déjà partagée avec cet ingénieur d’affaires.");
        }
        return toDto(manager);
    }

    @Transactional(readOnly = true)
    public List<BusinessManagerDto> listShares(UUID entryId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        requireOwnedEntry(entryId, ownerId);
        return shareRepository.findAllByCareerEntryIdOrderByCreatedAtAsc(entryId).stream()
                .map(CareerEntryShare::getBusinessManager)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void revoke(UUID entryId, UUID managerId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        requireOwnedEntry(entryId, ownerId);
        CareerEntryShare share = shareRepository.findByCareerEntryIdAndBusinessManagerId(entryId, managerId)
                .orElseThrow(() -> new NotFoundException("Partage d’entrée carrière introuvable."));
        shareRepository.delete(share);
    }

    @Transactional(readOnly = true)
    public List<SharedCareerEntryDto> listSharedWithCurrentManager() {
        UUID managerId = managerDirectory.requireBusinessManagerId();
        return shareRepository.findAllByBusinessManagerIdOrderByCreatedAtDesc(managerId).stream()
                .map(this::toSharedDto)
                .toList();
    }

    private CareerEntry requireOwnedEntry(UUID entryId, UUID ownerId) {
        return careerEntryRepository.findByIdAndUserId(entryId, ownerId)
                .orElseThrow(() -> new NotFoundException("Entrée de carrière introuvable."));
    }

    private SharedCareerEntryDto toSharedDto(CareerEntryShare share) {
        CareerEntry entry = share.getCareerEntry();
        String ownerName = userRepository.findById(entry.getUserId())
                .map(user -> (user.getFirstName() + " " + user.getLastName()).trim())
                .filter(name -> !name.isBlank())
                .orElse("Ancien collaborateur");
        try {
            return new SharedCareerEntryDto(entry.getId(), ownerName, entry.getTitle(), entry.getEntryDate(),
                    mapper.readTree(entry.getData()), share.getCreatedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Les données enregistrées de l’entrée carrière sont invalides.", ex);
        }
    }

    private BusinessManagerDto toDto(User manager) {
        return new BusinessManagerDto(manager.getId(), manager.getFirstName(), manager.getLastName(), manager.getEmail());
    }
}