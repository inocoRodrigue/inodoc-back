package com.esn.inodoc.domain.career;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Single, shared career-log template defined by business managers. It
 * describes the structure of a career-log entry (JSON form schema). Every
 * internal employee keeps their own log following this template.
 */
@Entity
@Table(name = "career_templates")
public class CareerTemplate extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON form schema describing the fields of a single career-log entry. */
    @Column(name = "entry_schema", columnDefinition = "text", nullable = false)
    private String entrySchema;

    @Column(name = "created_by")
    private UUID createdBy;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntrySchema() {
        return entrySchema;
    }

    public void setEntrySchema(String entrySchema) {
        this.entrySchema = entrySchema;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
