package com.esn.inodoc.domain.review;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * An internal employee's answer to a campaign. At most one submission per
 * (campaign, user): enforced by a unique constraint.
 */
@Entity
@Table(name = "review_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "user_id"}))
public class ReviewSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private ReviewCampaign campaign;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** JSON answers keyed by field id. */
    @Column(columnDefinition = "text", nullable = false)
    private String answers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    public ReviewCampaign getCampaign() {
        return campaign;
    }

    public void setCampaign(ReviewCampaign campaign) {
        this.campaign = campaign;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAnswers() {
        return answers;
    }

    public void setAnswers(String answers) {
        this.answers = answers;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
