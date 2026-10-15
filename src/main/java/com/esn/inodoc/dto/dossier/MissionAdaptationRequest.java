package com.esn.inodoc.dto.dossier;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record MissionAdaptationRequest(
        @Size(max = 30000, message = "Le descriptif est limité à 30 000 caractères") String missionDescription,
        @Size(max = 60000, message = "La transcription est limitée à 60 000 caractères") String clientTranscript,
                @Size(max = 80, message = "La langue cible est limitée à 80 caractères") String targetLanguage,
                @Size(max = 5000, message = "Les consignes sont limitées à 5 000 caractères") String instructions,
                MissionAdaptationMode mode,
        @AssertTrue(message = "L'autorisation de transmettre les contenus à l'IA est requise") boolean consent) {

        public MissionAdaptationRequest(String missionDescription, String clientTranscript, boolean consent) {
                this(missionDescription, clientTranscript, null, null, MissionAdaptationMode.MISSION, consent);
        }
}