package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Result of an AI generation from an uploaded PDF/Word document.
 *
 * @param kind "FORM" (fillable schema) or "REFERENCE" (consultative content)
 * @param title suggested title
 * @param data  the generated schema (for FORM) or content blocks (for REFERENCE)
 */
public record GenerationResultDto(
        String kind,
        String title,
        JsonNode data) {
}
