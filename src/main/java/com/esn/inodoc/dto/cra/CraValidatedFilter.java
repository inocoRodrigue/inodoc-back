package com.esn.inodoc.dto.cra;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

public record CraValidatedFilter(
        @Min(1) @Max(9999) Integer year,
        @Min(1) @Max(12) Integer month,
        UUID collaboratorId,
        @Size(max = 300) String validator,
        UUID projectId,
        String workLocation,
        @Size(max = 300) String search,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate declaredFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate declaredTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validatedTo,
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("31") Double minDays,
        @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("31") Double maxDays,
        String timeZone) {
    public static CraValidatedFilter empty() {
        return new CraValidatedFilter(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}