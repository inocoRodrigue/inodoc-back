package com.esn.inodoc.domain.review;

import com.esn.inodoc.common.domain.BaseEntity;
import com.esn.inodoc.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "review_submission_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"submission_id", "manager_user_id"}))
public class ReviewSubmissionShare extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ReviewSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_user_id", nullable = false)
    private User businessManager;

    public ReviewSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(ReviewSubmission submission) {
        this.submission = submission;
    }

    public User getBusinessManager() {
        return businessManager;
    }

    public void setBusinessManager(User businessManager) {
        this.businessManager = businessManager;
    }
}