package com.esn.inodoc.dto.cra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.esn.inodoc.domain.cra.CraProjectType;

import java.util.Set;
import java.util.UUID;

public record CraProjectRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        CraProjectType type,
                Set<UUID> collaboratorIds,
                @Size(max = 255) String clientName) {
        public CraProjectRequest(String name, String description, CraProjectType type, Set<UUID> collaboratorIds) {
                this(name, description, type, collaboratorIds, null);
        }
}