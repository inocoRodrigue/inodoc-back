package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Skill category (Backend, Frontend, DevOps, Cloud, Data, ...).
 */
@Entity
@Table(name = "skill_categories", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class SkillCategory extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
