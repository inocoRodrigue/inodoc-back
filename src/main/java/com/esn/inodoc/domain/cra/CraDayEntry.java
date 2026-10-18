package com.esn.inodoc.domain.cra;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "cra_day_entries", uniqueConstraints =
        @UniqueConstraint(columnNames = {"declaration_id", "work_date"}))
public class CraDayEntry extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declaration_id", nullable = false)
    private CraDeclaration declaration;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private CraProject project;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_location", length = 20)
    private CraWorkLocation workLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_part", nullable = false, length = 20)
    private CraDayPart dayPart = CraDayPart.FULL_DAY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "afternoon_project_id")
    private CraProject afternoonProject;

    @Enumerated(EnumType.STRING)
    @Column(name = "afternoon_work_location", length = 20)
    private CraWorkLocation afternoonWorkLocation;

    public CraDayPart getDayPart() { return dayPart; }
    public void setDayPart(CraDayPart value) { dayPart = value; }
    public CraProject getAfternoonProject() { return afternoonProject; }
    public void setAfternoonProject(CraProject value) { afternoonProject = value; }
    public CraWorkLocation getAfternoonWorkLocation() { return afternoonWorkLocation; }
    public void setAfternoonWorkLocation(CraWorkLocation value) { afternoonWorkLocation = value; }

    public CraDeclaration getDeclaration() { return declaration; }
    public void setDeclaration(CraDeclaration declaration) { this.declaration = declaration; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public CraProject getProject() { return project; }
    public void setProject(CraProject project) { this.project = project; }
    public CraWorkLocation getWorkLocation() { return workLocation; }
    public void setWorkLocation(CraWorkLocation workLocation) { this.workLocation = workLocation; }
}