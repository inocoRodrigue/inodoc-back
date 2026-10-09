package com.esn.inodoc.domain.review;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Fillable review form template created by a business manager. The schema
 * (sections and typed fields: open questions, single/multiple choice,
 * checkboxes, checkbox tables, anchors to reference documents...) is stored
 * as JSON and rendered dynamically.
 */
@Entity
@Table(name = "review_forms")
public class ReviewForm extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON schema: { sections: [ { title, description, fields: [...] } ] }. */
    @Column(columnDefinition = "text", nullable = false)
    private String schema;

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

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
