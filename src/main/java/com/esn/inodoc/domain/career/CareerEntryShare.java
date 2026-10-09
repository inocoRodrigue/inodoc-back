package com.esn.inodoc.domain.career;

import com.esn.inodoc.common.domain.BaseEntity;
import com.esn.inodoc.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "career_entry_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"career_entry_id", "manager_user_id"}))
public class CareerEntryShare extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_entry_id", nullable = false)
    private CareerEntry careerEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_user_id", nullable = false)
    private User businessManager;

    public CareerEntry getCareerEntry() {
        return careerEntry;
    }

    public void setCareerEntry(CareerEntry careerEntry) {
        this.careerEntry = careerEntry;
    }

    public User getBusinessManager() {
        return businessManager;
    }

    public void setBusinessManager(User businessManager) {
        this.businessManager = businessManager;
    }
}