package com.esn.inodoc.repo.review;

import com.esn.inodoc.domain.review.ReviewSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmission, UUID> {
    Optional<ReviewSubmission> findByCampaignIdAndUserId(UUID campaignId, UUID userId);

    List<ReviewSubmission> findByCampaignIdOrderByUpdatedAtDesc(UUID campaignId);

    List<ReviewSubmission> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
