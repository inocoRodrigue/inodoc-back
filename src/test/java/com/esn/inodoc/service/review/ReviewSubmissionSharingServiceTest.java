package com.esn.inodoc.service.review;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.review.ReviewCampaign;
import com.esn.inodoc.domain.review.ReviewSubmission;
import com.esn.inodoc.domain.review.ReviewSubmissionShare;
import com.esn.inodoc.domain.review.ReviewType;
import com.esn.inodoc.domain.review.SubmissionStatus;
import com.esn.inodoc.repo.review.ReviewSubmissionRepository;
import com.esn.inodoc.repo.review.ReviewSubmissionShareRepository;
import com.esn.inodoc.service.BusinessManagerDirectoryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewSubmissionSharingServiceTest {
    private final ReviewSubmissionRepository submissions = mock(ReviewSubmissionRepository.class);
    private final ReviewSubmissionShareRepository shares = mock(ReviewSubmissionShareRepository.class);
    private final BusinessManagerDirectoryService directory = mock(BusinessManagerDirectoryService.class);
    private final ReviewSubmissionSharingService service =
            new ReviewSubmissionSharingService(submissions, shares, directory);

    @Test
    void internalEmployeeCanShareSubmittedAnnualReview() {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        ReviewSubmission submission = submission(campaignId, ownerId, ReviewType.ANNUAL, SubmissionStatus.SUBMITTED);
        User manager = user(managerId, RoleName.BUSINESS_MANAGER);
        when(directory.requireInternalUserId()).thenReturn(ownerId);
        when(submissions.findByCampaignIdAndUserId(campaignId, ownerId)).thenReturn(Optional.of(submission));
        when(directory.requireActiveBusinessManager(managerId)).thenReturn(manager);
        when(shares.saveAndFlush(any(ReviewSubmissionShare.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.shareMyAnnualSubmission(campaignId, managerId);

        assertEquals(managerId, result.id());
        verify(shares).saveAndFlush(any(ReviewSubmissionShare.class));
    }

    @Test
    void draftAndSemiAnnualReviewsCannotBeShared() {
        UUID ownerId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(directory.requireInternalUserId()).thenReturn(ownerId);
        when(submissions.findByCampaignIdAndUserId(campaignId, ownerId))
                .thenReturn(Optional.of(submission(campaignId, ownerId, ReviewType.ANNUAL, SubmissionStatus.DRAFT)))
                .thenReturn(Optional.of(submission(campaignId, ownerId, ReviewType.SEMI_ANNUAL, SubmissionStatus.SUBMITTED)));

        assertThrows(BadRequestException.class, () -> service.shareMyAnnualSubmission(campaignId, managerId));
        assertThrows(BadRequestException.class, () -> service.shareMyAnnualSubmission(campaignId, managerId));
        verify(shares, never()).saveAndFlush(any(ReviewSubmissionShare.class));
    }

    @Test
    void managerReceivesOnlySubmissionsSharedWithTheirAccount() {
        UUID campaignId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(directory.requireBusinessManagerId()).thenReturn(managerId);
        when(shares.findSharedSubmissions(campaignId, managerId, SubmissionStatus.SUBMITTED))
                .thenReturn(java.util.List.of());

        assertEquals(0, service.listSharedAnnualSubmissions(campaignId).size());
        verify(shares).findSharedSubmissions(campaignId, managerId, SubmissionStatus.SUBMITTED);
    }

    private ReviewSubmission submission(UUID campaignId, UUID ownerId, ReviewType type, SubmissionStatus status) {
        ReviewCampaign campaign = new ReviewCampaign();
        campaign.setId(campaignId);
        campaign.setType(type);
        ReviewSubmission submission = new ReviewSubmission();
        submission.setCampaign(campaign);
        submission.setUserId(ownerId);
        submission.setStatus(status);
        return submission;
    }

    private User user(UUID id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setFirstName("Business");
        user.setLastName("Manager");
        user.setEmail("manager@example.test");
        user.setRoles(Set.of(role));
        return user;
    }
}