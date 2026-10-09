package com.esn.inodoc.service.review;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.review.ReviewSubmission;
import com.esn.inodoc.domain.review.ReviewSubmissionShare;
import com.esn.inodoc.domain.review.ReviewType;
import com.esn.inodoc.domain.review.SubmissionStatus;
import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.repo.review.ReviewSubmissionRepository;
import com.esn.inodoc.repo.review.ReviewSubmissionShareRepository;
import com.esn.inodoc.service.BusinessManagerDirectoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewSubmissionSharingService {
    private final ReviewSubmissionRepository submissionRepository;
    private final ReviewSubmissionShareRepository shareRepository;
    private final BusinessManagerDirectoryService managerDirectory;

    public ReviewSubmissionSharingService(ReviewSubmissionRepository submissionRepository,
                                          ReviewSubmissionShareRepository shareRepository,
                                          BusinessManagerDirectoryService managerDirectory) {
        this.submissionRepository = submissionRepository;
        this.shareRepository = shareRepository;
        this.managerDirectory = managerDirectory;
    }

    @Transactional
    public BusinessManagerDto shareMyAnnualSubmission(UUID campaignId, UUID managerId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        ReviewSubmission submission = requireOwnedAnnualSubmission(campaignId, ownerId);
        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Validez l’entretien annuel avant de le partager.");
        }
        User manager = managerDirectory.requireActiveBusinessManager(managerId);
        if (shareRepository.existsBySubmissionIdAndBusinessManagerId(submission.getId(), managerId)) {
            throw new ConflictException("Cet entretien annuel est déjà partagé avec cet ingénieur d’affaires.");
        }

        ReviewSubmissionShare share = new ReviewSubmissionShare();
        share.setSubmission(submission);
        share.setBusinessManager(manager);
        try {
            shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Cet entretien annuel est déjà partagé avec cet ingénieur d’affaires.");
        }
        return toDto(manager);
    }

    @Transactional(readOnly = true)
    public List<BusinessManagerDto> listMyAnnualSubmissionShares(UUID campaignId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        ReviewSubmission submission = requireOwnedAnnualSubmission(campaignId, ownerId);
        return shareRepository.findAllBySubmissionIdOrderByCreatedAtAsc(submission.getId()).stream()
                .map(ReviewSubmissionShare::getBusinessManager)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void revokeMyAnnualSubmissionShare(UUID campaignId, UUID managerId) {
        UUID ownerId = managerDirectory.requireInternalUserId();
        ReviewSubmission submission = requireOwnedAnnualSubmission(campaignId, ownerId);
        ReviewSubmissionShare share = shareRepository
                .findBySubmissionIdAndBusinessManagerId(submission.getId(), managerId)
                .orElseThrow(() -> new NotFoundException("Partage d’entretien annuel introuvable."));
        shareRepository.delete(share);
    }

    @Transactional(readOnly = true)
    public List<ReviewSubmission> listSharedAnnualSubmissions(UUID campaignId) {
        UUID managerId = managerDirectory.requireBusinessManagerId();
        return shareRepository.findSharedSubmissions(campaignId, managerId, SubmissionStatus.SUBMITTED).stream()
                .map(ReviewSubmissionShare::getSubmission)
                .filter(submission -> submission.getCampaign().getType() == ReviewType.ANNUAL)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isSharedWithBusinessManager(UUID submissionId, UUID managerId) {
        return shareRepository.existsBySubmissionIdAndBusinessManagerId(submissionId, managerId);
    }

    private ReviewSubmission requireOwnedAnnualSubmission(UUID campaignId, UUID ownerId) {
        ReviewSubmission submission = submissionRepository.findByCampaignIdAndUserId(campaignId, ownerId)
                .orElseThrow(() -> new NotFoundException("Réponse à l’entretien annuel introuvable."));
        if (submission.getCampaign().getType() != ReviewType.ANNUAL) {
            throw new BadRequestException("Seuls les entretiens annuels peuvent être partagés.");
        }
        return submission;
    }

    private BusinessManagerDto toDto(User manager) {
        return new BusinessManagerDto(manager.getId(), manager.getFirstName(), manager.getLastName(), manager.getEmail());
    }
}