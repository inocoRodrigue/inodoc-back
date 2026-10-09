package com.esn.inodoc.dto.dossier;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MissionAdaptationRequest(
        @NotBlank(message = "Le descriptif de mission est requis")
        @Size(max = 30000, message = "Le descriptif est limité à 30 000 caractères") String missionDescription,
        @Size(max = 60000, message = "La transcription est limitée à 60 000 caractères") String clientTranscript,
        @AssertTrue(message = "L'autorisation de transmettre les contenus à l'IA est requise") boolean consent) {
}