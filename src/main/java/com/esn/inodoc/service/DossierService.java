package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.dossier.DossierMapper;
import com.esn.inodoc.dto.dossier.DossierSummaryDto;
import com.esn.inodoc.dto.dossier.DossierUpsertRequest;
import com.esn.inodoc.repo.CompetenceDossierRepository;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Management of competence dossier versions attached to a profile.
 */
@Service
public class DossierService {

    private final CompetenceDossierRepository dossierRepository;
    private final ProfileService profileService;
    private final TechSkillService techSkillService;
    private final PdfService pdfService;
    private final S3ImageStorage storage;
    private final CompanySettingsService companySettingsService;

    public DossierService(CompetenceDossierRepository dossierRepository,
                          ProfileService profileService,
                          TechSkillService techSkillService,
                          PdfService pdfService,
                          S3ImageStorage storage,
                          CompanySettingsService companySettingsService) {
        this.dossierRepository = dossierRepository;
        this.profileService = profileService;
        this.techSkillService = techSkillService;
        this.pdfService = pdfService;
        this.storage = storage;
        this.companySettingsService = companySettingsService;
    }

    @Transactional(readOnly = true)
    public List<DossierSummaryDto> listForProfile(UUID profileId) {
        profileService.assertCanAccess(profileId);
        int staleAfterMonths = companySettingsService.get().staleDossierMonths();
        return dossierRepository
                .findByProfileIdOrderByMasterDescCreatedAtAsc(profileId)
            .stream().map(dossier -> DossierMapper.toSummary(dossier, staleAfterMonths)).toList();
    }

    @Transactional(readOnly = true)
    public DossierDto get(UUID dossierId) {
        CompetenceDossier dossier = requireAccessible(dossierId);
        return DossierMapper.toDto(dossier, storage::signedUrl);
    }

    @Transactional
    public DossierDto create(UUID profileId, DossierUpsertRequest request) {
        Profile profile = profileService.requireProfile(profileId);
        profileService.assertCanAccess(profileId);

        List<CompetenceDossier> existing = dossierRepository.findByProfileId(profileId);
        if (!existing.isEmpty() && Boolean.TRUE.equals(request.master()) && !profileService.canManageAll()) {
            throw new AccessDeniedException("Seul un gestionnaire peut remplacer la version master");
        }

        CompetenceDossier dossier = new CompetenceDossier();
        dossier.setProfile(profile);
        DossierMapper.apply(dossier, request, storage::storedUrl);

        boolean makeMaster = existing.isEmpty() || Boolean.TRUE.equals(request.master());
        if (makeMaster) {
            existing.forEach(d -> d.setMaster(false));
            dossierRepository.saveAll(existing);
            dossier.setMaster(true);
        }

        dossier = dossierRepository.save(dossier);
        techSkillService.harvest(dossier);
        return DossierMapper.toDto(dossier, storage::signedUrl);
    }

    @Transactional
    public DossierDto update(UUID dossierId, DossierUpsertRequest request) {
        CompetenceDossier dossier = requireAccessible(dossierId);
        if (dossier.isMaster() && !profileService.canManageAll()) {
            throw new AccessDeniedException(
                    "La version de référence (master) ne peut être modifiée que par un administrateur ou un ingénieur d'affaires");
        }
        DossierMapper.apply(dossier, request, storage::storedUrl);
        dossier = dossierRepository.save(dossier);
        techSkillService.harvest(dossier);
        return DossierMapper.toDto(dossier, storage::signedUrl);
    }

    @Transactional
    public DossierDto setMaster(UUID dossierId) {
        CompetenceDossier dossier = requireDossier(dossierId);
        if (!profileService.canManageAll()) {
            throw new AccessDeniedException("Seul un ingenieur d'affaires peut definir la version de reference");
        }
        UUID profileId = dossier.getProfile().getId();
        List<CompetenceDossier> all = dossierRepository
                .findByProfileId(profileId);
        all.forEach(d -> d.setMaster(d.getId().equals(dossierId)));
        dossierRepository.saveAll(all);
        return DossierMapper.toDto(dossier, storage::signedUrl);
    }

    @Transactional
    public void delete(UUID dossierId) {
        CompetenceDossier dossier = requireDossier(dossierId);
        if (!profileService.canManageAll()) {
            throw new AccessDeniedException("Seul un ingénieur d'affaires peut supprimer une version");
        }
        UUID profileId = dossier.getProfile().getId();
        List<CompetenceDossier> all = dossierRepository
                .findByProfileId(profileId);
        if (all.size() <= 1) {
            throw new BadRequestException("Impossible de supprimer la dernière version du dossier");
        }
        boolean wasMaster = dossier.isMaster();
        dossierRepository.delete(dossier);

        if (wasMaster) {
            all.stream()
                    .filter(d -> !d.getId().equals(dossierId))
                    .min(Comparator.comparing(CompetenceDossier::getCreatedAt))
                    .ifPresent(d -> {
                        d.setMaster(true);
                        dossierRepository.save(d);
                    });
        }
    }

    /**
     * Generates the dossier PDF within the transaction so that the
     * lazy associations (profile, experiences, skills...) remain
     * initializable while the Hibernate session is open.
     */
    @Transactional(readOnly = true)
    public GeneratedDocument generatePdf(UUID dossierId) {
        CompetenceDossier dossier = requireAccessible(dossierId);
        byte[] pdf = pdfService.generateDossierPdf(dossier);
        Profile profile = dossier.getProfile();
        String filename = "Dossier_"
                + (profile.getFirstName() + "_" + profile.getLastName()).replaceAll("[^A-Za-z0-9_]", "")
                + ".pdf";
        return new GeneratedDocument(filename, pdf);
    }

    /** Generated binary document (file name + content). */
    public record GeneratedDocument(String filename, byte[] content) {
    }

    private CompetenceDossier requireAccessible(UUID dossierId) {
        CompetenceDossier dossier = requireDossier(dossierId);
        profileService.assertCanAccess(dossier.getProfile().getId());
        return dossier;
    }

    private CompetenceDossier requireDossier(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new NotFoundException("Version de dossier introuvable"));
    }
}
