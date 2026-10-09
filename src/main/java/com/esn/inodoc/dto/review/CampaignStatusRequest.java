package com.esn.inodoc.dto.review;

import com.esn.inodoc.domain.review.CampaignStatus;
import jakarta.validation.constraints.NotNull;

public record CampaignStatusRequest(@NotNull CampaignStatus status) {
}
