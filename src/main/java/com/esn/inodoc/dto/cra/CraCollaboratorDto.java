package com.esn.inodoc.dto.cra;

import java.util.UUID;

public record CraCollaboratorDto(UUID id, String firstName, String lastName, String email) {
}