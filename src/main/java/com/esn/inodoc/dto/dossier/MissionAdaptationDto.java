package com.esn.inodoc.dto.dossier;

import java.util.List;

public record MissionAdaptationDto(DossierUpsertRequest dossier, List<String> gaps,
                                   boolean incomplete, List<String> warnings) {
}