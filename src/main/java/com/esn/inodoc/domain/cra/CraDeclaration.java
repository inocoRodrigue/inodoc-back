package com.esn.inodoc.domain.cra;

import com.esn.inodoc.common.domain.BaseEntity;
import com.esn.inodoc.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cra_declarations", uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_id", "year", "month"}))
public class CraDeclaration extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User collaborator;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CraDeclarationStatus status = CraDeclarationStatus.PENDING;

    @Column(name = "declared_at")
    private Instant declaredAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String value) { rejectionReason = value; }

    @OneToMany(mappedBy = "declaration", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("workDate ASC")
    private List<CraDayEntry> entries = new ArrayList<>();

    public User getCollaborator() { return collaborator; }
    public void setCollaborator(User collaborator) { this.collaborator = collaborator; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public CraDeclarationStatus getStatus() { return status; }
    public void setStatus(CraDeclarationStatus status) { this.status = status; }
    public Instant getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(Instant declaredAt) { this.declaredAt = declaredAt; }
    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant validatedAt) { this.validatedAt = validatedAt; }
    public User getValidatedBy() { return validatedBy; }
    public void setValidatedBy(User validatedBy) { this.validatedBy = validatedBy; }
    public List<CraDayEntry> getEntries() { return entries; }
    public void setEntries(List<CraDayEntry> entries) { this.entries = entries; }
}