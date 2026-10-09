package com.esn.inodoc.dto.share;

import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.dto.dossier.DossierDto;

/**
 * Response returned during public consultation of a shared link.
 * The profile is identified only by its initials (client anonymization).
 */
public record SharedDossierView(
        DossierDto dossier,
        String profileName,
        CompanyContactDto company,
        boolean editable) {
}
