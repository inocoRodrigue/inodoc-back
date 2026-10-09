package com.esn.inodoc.repo.review;

import com.esn.inodoc.domain.review.ReviewSubmissionShare;
import com.esn.inodoc.domain.review.SubmissionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSubmissionShareRepository extends JpaRepository<ReviewSubmissionShare, UUID> {
    @EntityGraph(attributePaths = "businessManager")
    List<ReviewSubmissionShare> findAllBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    Optional<ReviewSubmissionShare> findBySubmissionIdAndBusinessManagerId(UUID submissionId, UUID businessManagerId);

    boolean existsBySubmissionIdAndBusinessManagerId(UUID submissionId, UUID businessManagerId);

    @Query("select share from ReviewSubmissionShare share join fetch share.submission submission "
            + "join fetch submission.campaign campaign join fetch campaign.form "
            + "where campaign.id = :campaignId and share.businessManager.id = :businessManagerId "
            + "and submission.status = :status order by submission.updatedAt desc")
    List<ReviewSubmissionShare> findSharedSubmissions(
            @Param("campaignId") UUID campaignId,
            @Param("businessManagerId") UUID businessManagerId,
            @Param("status") SubmissionStatus status);
}