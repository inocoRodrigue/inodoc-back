package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Elementary permission (global reference) used by the RBAC.
 * Exemple de code : PROFILE_READ, PROFILE_WRITE, USER_MANAGE...
 */
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column
    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
