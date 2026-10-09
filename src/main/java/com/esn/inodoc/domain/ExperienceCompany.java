package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Professional experience at a company (client / employer),
 * containing one or more projects.
 */
@Entity
@Table(name = "experiences")
public class ExperienceCompany extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dossier_id", nullable = false)
    private CompetenceDossier dossier;

    @Column(nullable = false)
    private String company;

    /** Start date of the experience. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** End date; null = position still ongoing. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "role_title")
    private String roleTitle;

    @Column(columnDefinition = "text")
    private String context;

    @Column(nullable = false)
    private int position = 0;

    @OneToMany(mappedBy = "experience", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ExperienceProject> projects = new ArrayList<>();

    public void addProject(ExperienceProject project) {
        project.setExperience(this);
        this.projects.add(project);
    }

    public CompetenceDossier getDossier() {
        return dossier;
    }

    public void setDossier(CompetenceDossier dossier) {
        this.dossier = dossier;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public List<ExperienceProject> getProjects() {
        return projects;
    }

    public void setProjects(List<ExperienceProject> projects) {
        this.projects = projects;
    }
}
