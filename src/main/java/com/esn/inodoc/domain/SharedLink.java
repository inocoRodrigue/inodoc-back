package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Secure share link to a competence dossier version.
 */
@Entity
@Table(name = "shared_links")
public class SharedLink extends BaseEntity {

    /** Public token present in the URL (random, non-guessable). */
    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dossier_id", nullable = false)
    private CompetenceDossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareType type;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_opens")
    private Integer maxOpens;

    @Column(name = "open_count", nullable = false)
    private int openCount = 0;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_by")
    private UUID createdBy;

    public boolean isPasswordProtected() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isOpenLimitReached() {
        return maxOpens != null && openCount >= maxOpens;
    }

    public boolean isActive() {
        return !revoked && !isExpired() && !isOpenLimitReached();
    }

    public boolean isEditable() {
        return type == ShareType.COLLABORATOR_EDIT;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public CompetenceDossier getDossier() {
        return dossier;
    }

    public void setDossier(CompetenceDossier dossier) {
        this.dossier = dossier;
    }

    public ShareType getType() {
        return type;
    }

    public void setType(ShareType type) {
        this.type = type;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getMaxOpens() {
        return maxOpens;
    }

    public void setMaxOpens(Integer maxOpens) {
        this.maxOpens = maxOpens;
    }

    public int getOpenCount() {
        return openCount;
    }

    public void setOpenCount(int openCount) {
        this.openCount = openCount;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
