package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Project carried out within a company experience.
 */
@Entity
@Table(name = "experience_projects")
public class ExperienceProject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experience_id", nullable = false)
    private ExperienceCompany experience;

    @Column
    private String name;

    @Column(columnDefinition = "text")
    private String context;

    @Column
    private String team;

    @Column
    private String methodology;

    /** Tasks performed, one per line. */
    @Column(columnDefinition = "text")
    private String tasks;

    @Column(columnDefinition = "text")
    private String technologies;

    @Column(nullable = false)
    private int position = 0;

    public ExperienceCompany getExperience() {
        return experience;
    }

    public void setExperience(ExperienceCompany experience) {
        this.experience = experience;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getMethodology() {
        return methodology;
    }

    public void setMethodology(String methodology) {
        this.methodology = methodology;
    }

    public String getTasks() {
        return tasks;
    }

    public void setTasks(String tasks) {
        this.tasks = tasks;
    }

    public String getTechnologies() {
        return technologies;
    }

    public void setTechnologies(String technologies) {
        this.technologies = technologies;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
