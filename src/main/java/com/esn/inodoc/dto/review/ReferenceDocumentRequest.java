package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record ReferenceDocumentRequest(
        @NotBlank String title,
        String description,
        String category,
        JsonNode content) {
}
