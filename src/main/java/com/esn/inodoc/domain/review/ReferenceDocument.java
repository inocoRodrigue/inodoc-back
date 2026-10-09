package com.esn.inodoc.domain.review;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Consultative (read-only) document that informs the person filling a review
 * form: seniority grids (Junior 1/2, Confirmé, Tech Lead), points scales, etc.
 * The structured content is stored as JSON and rendered read-only.
 */
@Entity
@Table(name = "reference_documents")
public class ReferenceDocument extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** Free classification, e.g. "Junior 1", "Tech Lead", "Barème". */
    @Column
    private String category;

    /** JSON content blocks (headings, paragraphs, lists, tables...). */
    @Column(columnDefinition = "text", nullable = false)
    private String content;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
