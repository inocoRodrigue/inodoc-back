package com.esn.inodoc.dto.career;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CareerTemplateRequest(
        @NotBlank String title,
        String description,
        JsonNode entrySchema) {
}
