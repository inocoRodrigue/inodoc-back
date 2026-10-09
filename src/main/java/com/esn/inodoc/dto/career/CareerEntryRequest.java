package com.esn.inodoc.dto.career;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CareerEntryRequest(
        @NotBlank String title,
        LocalDate entryDate,
        JsonNode data) {
}
