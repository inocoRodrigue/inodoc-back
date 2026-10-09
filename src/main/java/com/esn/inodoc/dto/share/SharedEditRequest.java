package com.esn.inodoc.dto.share;

import com.esn.inodoc.dto.dossier.DossierUpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Update of a dossier version via an editable share link.
 */
public record SharedEditRequest(
        String password,
        @NotNull @Valid DossierUpsertRequest dossier) {
}
