package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record ReviewFormRequest(
        @NotBlank String title,
        String description,
        JsonNode schema) {
}
