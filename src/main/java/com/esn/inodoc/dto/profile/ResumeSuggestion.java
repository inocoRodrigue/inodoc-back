package com.esn.inodoc.dto.profile;

import com.fasterxml.jackson.databind.JsonNode;

public record ResumeSuggestion(
        String type,
        String fieldPath,
        JsonNode currentValue,
        JsonNode proposedValue,
        String rationale) {
}