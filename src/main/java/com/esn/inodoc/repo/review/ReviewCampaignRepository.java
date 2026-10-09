package com.esn.inodoc.repo.review;

import com.esn.inodoc.domain.review.CampaignStatus;
import com.esn.inodoc.domain.review.ReviewCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewCampaignRepository extends JpaRepository<ReviewCampaign, UUID> {

    @Query("SELECT c FROM ReviewCampaign c JOIN FETCH c.form ORDER BY c.createdAt DESC")
    List<ReviewCampaign> findAllWithForm();

    @Query("SELECT c FROM ReviewCampaign c JOIN FETCH c.form WHERE c.status = :status ORDER BY c.createdAt DESC")
    List<ReviewCampaign> findByStatusWithForm(CampaignStatus status);

    boolean existsByFormId(UUID formId);
}
