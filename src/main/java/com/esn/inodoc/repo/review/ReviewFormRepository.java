package com.esn.inodoc.repo.review;

import com.esn.inodoc.domain.review.ReviewForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewFormRepository extends JpaRepository<ReviewForm, UUID> {
    List<ReviewForm> findAllByOrderByCreatedAtDesc();
}
