package com.esn.inodoc.domain.career;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single entry in an employee's career log. Entries accumulate over the
 * years. Their captured data is preserved and displayed using the current
 * shared {@link CareerTemplate}.
 */
@Entity
@Table(name = "career_entries")
public class CareerEntry extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    /** JSON answers keyed by field id, following the template entry schema. */
    @Column(columnDefinition = "text", nullable = false)
    private String data;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
