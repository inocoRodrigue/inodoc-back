package com.esn.inodoc.dto.review;

import com.esn.inodoc.domain.review.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SubmissionDto(
        UUID id,
        UUID campaignId,
        UUID userId,
        String userName,
        JsonNode answers,
        SubmissionStatus status,
        Instant submittedAt,
        Instant updatedAt) {
}
