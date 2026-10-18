package com.esn.inodoc.domain.cra;

import com.esn.inodoc.common.domain.BaseEntity;
import com.esn.inodoc.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cra_projects")
public class CraProject extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "client_name", length = 255)
    private String clientName;
    public String getClientName() { return clientName; }
    public void setClientName(String value) { clientName = value; }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CraProjectType type;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;

    @Column(nullable = false)
    private boolean archived;
    public boolean isArchived() { return archived; }
    public void setArchived(boolean value) { archived = value; }

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "cra_project_collaborators",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> collaborators = new HashSet<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CraProjectType getType() { return type; }
    public void setType(CraProjectType type) { this.type = type; }
    public boolean isSystemDefined() { return systemDefined; }
    public void setSystemDefined(boolean systemDefined) { this.systemDefined = systemDefined; }
    public Set<User> getCollaborators() { return collaborators; }
    public void setCollaborators(Set<User> collaborators) { this.collaborators = collaborators; }
}