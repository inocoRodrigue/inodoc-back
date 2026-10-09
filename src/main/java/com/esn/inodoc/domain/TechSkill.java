package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Item of the technical skills catalog offered as autocompletion.
 * Seeded at startup and completed automatically when a user
 * enters an unknown skill.
 */
@Entity
@Table(name = "tech_skills", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class TechSkill extends BaseEntity {

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
