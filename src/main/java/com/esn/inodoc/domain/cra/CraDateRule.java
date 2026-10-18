package com.esn.inodoc.domain.cra;

import jakarta.persistence.*;

@Embeddable
public class CraDateRule {
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private CraDateRuleType type;
    @Column(length = 255)
    private String label;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private CraProject project;
    public CraDateRuleType getType() { return type; }
    public void setType(CraDateRuleType value) { type = value; }
    public String getLabel() { return label; }
    public void setLabel(String value) { label = value; }
    public CraProject getProject() { return project; }
    public void setProject(CraProject value) { project = value; }
}