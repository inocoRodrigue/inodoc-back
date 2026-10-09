package com.esn.inodoc.dto.career;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SharedCareerEntryDto(
        UUID id,
        String ownerName,
        String title,
        LocalDate entryDate,
        JsonNode data,
        Instant sharedAt) {
}