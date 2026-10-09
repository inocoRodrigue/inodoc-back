package com.esn.inodoc.dto.review;

import com.esn.inodoc.domain.review.CampaignStatus;
import com.esn.inodoc.domain.review.ReviewType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CampaignDto(
        UUID id,
        String title,
        ReviewType type,
        UUID formId,
        String formTitle,
        LocalDate startDate,
        LocalDate endDate,
        CampaignStatus status,
        Instant createdAt,
        JsonNode formSchema) {
}
