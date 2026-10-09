package com.esn.inodoc.dto.company;

/**
 * ESN contact information displayed in the dossier header.
 */
public record CompanyContactDto(
        String address,
        String phone,
        String email,
        String emailSignature,
        String logoUrl) {
}
