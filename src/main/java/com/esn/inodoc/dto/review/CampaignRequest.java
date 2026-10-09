package com.esn.inodoc.dto.review;

import com.esn.inodoc.domain.review.ReviewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CampaignRequest(
        @NotBlank String title,
        @NotNull ReviewType type,
        @NotNull UUID formId,
        LocalDate startDate,
        LocalDate endDate) {
}
