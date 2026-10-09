package com.esn.inodoc.domain;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Version of a competence dossier attached to a profile.
 * A profile may own several versions; the business manager designates
 * the reference version via the {@code master} flag.
 */
@Entity
@Table(name = "competence_dossiers")
public class CompetenceDossier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    /** Version name (e.g. "Version master", "Version mission banque"). */
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean master = false;

    @Column
    private String headline;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "expertise_domain")
    private String expertiseDomain;

    @Column(columnDefinition = "text")
    private String summary;

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<SkillGroup> skillGroups = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ExperienceCompany> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Certification> certifications = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Education> educations = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<LanguageSkill> languages = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DossierReference> references = new ArrayList<>();

    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TestResult> testResults = new ArrayList<>();

    public void addSkillGroup(SkillGroup group) {
        group.setDossier(this);
        this.skillGroups.add(group);
    }

    public void addExperience(ExperienceCompany experience) {
        experience.setDossier(this);
        this.experiences.add(experience);
    }

    public void addCertification(Certification certification) {
        certification.setDossier(this);
        this.certifications.add(certification);
    }

    public void addEducation(Education education) {
        education.setDossier(this);
        this.educations.add(education);
    }

    public void addLanguage(LanguageSkill language) {
        language.setDossier(this);
        this.languages.add(language);
    }

    public void addReference(DossierReference reference) {
        reference.setDossier(this);
        this.references.add(reference);
    }

    public void addTestResult(TestResult testResult) {
        testResult.setDossier(this);
        this.testResults.add(testResult);
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getExpertiseDomain() {
        return expertiseDomain;
    }

    public void setExpertiseDomain(String expertiseDomain) {
        this.expertiseDomain = expertiseDomain;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<SkillGroup> getSkillGroups() {
        return skillGroups;
    }

    public void setSkillGroups(List<SkillGroup> skillGroups) {
        this.skillGroups = skillGroups;
    }

    public List<ExperienceCompany> getExperiences() {
        return experiences;
    }

    public void setExperiences(List<ExperienceCompany> experiences) {
        this.experiences = experiences;
    }

    public List<Certification> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<Certification> certifications) {
        this.certifications = certifications;
    }

    public List<Education> getEducations() {
        return educations;
    }

    public void setEducations(List<Education> educations) {
        this.educations = educations;
    }

    public List<LanguageSkill> getLanguages() {
        return languages;
    }

    public void setLanguages(List<LanguageSkill> languages) {
        this.languages = languages;
    }

    public List<DossierReference> getReferences() {
        return references;
    }

    public void setReferences(List<DossierReference> references) {
        this.references = references;
    }

    public List<TestResult> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<TestResult> testResults) {
        this.testResults = testResults;
    }
}
