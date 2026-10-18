package com.esn.inodoc.dto.cra;

import com.esn.inodoc.domain.cra.CraProjectType;

import java.util.List;
import java.util.UUID;

public record CraProjectDto(
        UUID id,
        String code,
        String name,
        String description,
        CraProjectType type,
        boolean systemDefined,
                List<CraCollaboratorDto> collaborators,
                String clientName) {
        public CraProjectDto(UUID id, String code, String name, String description, CraProjectType type, boolean systemDefined, List<CraCollaboratorDto> collaborators) {
                this(id, code, name, description, type, systemDefined, collaborators, null);
        }
}