package com.esn.inodoc.dto.share;

import com.esn.inodoc.domain.ShareType;

import java.time.Instant;
import java.util.UUID;

public record SharedLinkDto(
        UUID id,
        String token,
        String url,
        ShareType type,
        UUID dossierId,
        String profileName,
        String dossierLabel,
        Instant expiresAt,
        Integer maxOpens,
        int openCount,
        boolean passwordProtected,
        boolean revoked,
        boolean active) {
}
