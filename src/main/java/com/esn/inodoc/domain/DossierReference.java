package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Reachable professional reference attached to a dossier version.
 * All fields are optional (at least one contact is expected).
 */
@Entity
@Table(name = "dossier_references")
public class DossierReference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dossier_id", nullable = false)
    private CompetenceDossier dossier;

    @Column
    private String name;

    @Column
    private String email;

    @Column
    private String phone;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 1024)
    private String link;

    @Column(nullable = false)
    private int position = 0;

    public CompetenceDossier getDossier() {
        return dossier;
    }

    public void setDossier(CompetenceDossier dossier) {
        this.dossier = dossier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
