package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * ESN contact information displayed in the header of competence
 * dossiers (address, phone, email). A single row for the application.
 */
@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    @Column(length = 512)
    private String address;

    @Column
    private String phone;

    @Column
    private String email;

    @Column(name = "email_signature", columnDefinition = "TEXT")
    private String emailSignature;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmailSignature() {
        return emailSignature;
    }

    public void setEmailSignature(String emailSignature) {
        this.emailSignature = emailSignature;
    }
}
