package com.esn.inodoc.dto.dossier;

import com.esn.inodoc.domain.Certification;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.DossierReference;
import com.esn.inodoc.domain.Education;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.TestResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Conversion between the {@link CompetenceDossier} entity and its DTOs.
 */
public final class DossierMapper {

    private DossierMapper() {
    }

    public static DossierDto toDto(CompetenceDossier d) {
        return toDto(d, UnaryOperator.identity());
    }

    public static DossierDto toDto(CompetenceDossier d, UnaryOperator<String> imageUrlResolver) {
        return new DossierDto(
                d.getId(),
                d.getProfile() != null ? d.getProfile().getId() : null,
                d.getLabel(),
                d.isMaster(),
                d.getHeadline(),
                d.getYearsOfExperience(),
                d.getExpertiseDomain(),
                d.getSummary(),
                d.getSkillGroups().stream().map(DossierMapper::toDto).toList(),
                d.getExperiences().stream().map(DossierMapper::toDto).toList(),
                d.getCertifications().stream().map(item -> toDto(item, imageUrlResolver)).toList(),
                d.getEducations().stream().map(item -> toDto(item, imageUrlResolver)).toList(),
                d.getLanguages().stream().map(DossierMapper::toDto).toList(),
                d.getReferences().stream().map(DossierMapper::toDto).toList(),
                d.getTestResults().stream().map(item -> toDto(item, imageUrlResolver)).toList(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    public static DossierSummaryDto toSummary(CompetenceDossier d) {
        return new DossierSummaryDto(
                d.getId(),
                d.getLabel(),
                d.isMaster(),
                d.getHeadline(),
                d.getYearsOfExperience(),
                d.getExpertiseDomain(),
                d.getUpdatedAt());
    }

    /** Applies the payload content (except the master flag, handled by the service). */
    public static void apply(CompetenceDossier d, DossierUpsertRequest r) {
        d.setLabel(r.label());
        d.setHeadline(r.headline());
        d.setYearsOfExperience(r.yearsOfExperience());
        d.setExpertiseDomain(r.expertiseDomain());
        d.setSummary(r.summary());

        d.getSkillGroups().clear();
        AtomicInteger sgPos = new AtomicInteger();
        emptyIfNull(r.skillGroups()).forEach(dto -> {
            SkillGroup g = toEntity(dto);
            g.setPosition(sgPos.getAndIncrement());
            d.addSkillGroup(g);
        });

        d.getExperiences().clear();
        AtomicInteger expPos = new AtomicInteger();
        emptyIfNull(r.experiences()).forEach(dto -> {
            ExperienceCompany e = toEntity(dto);
            e.setPosition(expPos.getAndIncrement());
            d.addExperience(e);
        });

        d.getCertifications().clear();
        AtomicInteger certPos = new AtomicInteger();
        emptyIfNull(r.certifications()).forEach(dto -> {
            Certification c = toEntity(dto);
            c.setPosition(certPos.getAndIncrement());
            d.addCertification(c);
        });

        d.getEducations().clear();
        AtomicInteger eduPos = new AtomicInteger();
        emptyIfNull(r.educations()).forEach(dto -> {
            Education e = toEntity(dto);
            e.setPosition(eduPos.getAndIncrement());
            d.addEducation(e);
        });

        d.getLanguages().clear();
        AtomicInteger langPos = new AtomicInteger();
        emptyIfNull(r.languages()).forEach(dto -> {
            LanguageSkill l = toEntity(dto);
            l.setPosition(langPos.getAndIncrement());
            d.addLanguage(l);
        });

        d.getReferences().clear();
        AtomicInteger refPos = new AtomicInteger();
        emptyIfNull(r.references()).forEach(dto -> {
            DossierReference ref = toEntity(dto);
            ref.setPosition(refPos.getAndIncrement());
            d.addReference(ref);
        });

        d.getTestResults().clear();
        AtomicInteger testPos = new AtomicInteger();
        emptyIfNull(r.testResults()).forEach(dto -> {
            TestResult t = toEntity(dto);
            t.setPosition(testPos.getAndIncrement());
            d.addTestResult(t);
        });
    }

    private static <T> List<T> emptyIfNull(List<T> list) {
        return Optional.ofNullable(list).orElse(List.of());
    }

    public static void apply(CompetenceDossier dossier, DossierUpsertRequest request,
                             UnaryOperator<String> storedUrlResolver) {
        apply(dossier, request);
        dossier.getCertifications().forEach(item -> item.setImageUrl(storedUrlResolver.apply(item.getImageUrl())));
        dossier.getEducations().forEach(item -> item.setImageUrl(storedUrlResolver.apply(item.getImageUrl())));
        dossier.getTestResults().forEach(item -> item.setImageUrl(storedUrlResolver.apply(item.getImageUrl())));
    }

    // --- child mappings ---
    private static SkillGroupDto toDto(SkillGroup s) {
        return new SkillGroupDto(s.getId(), s.getCategory(), s.getContent());
    }

    private static SkillGroup toEntity(SkillGroupDto dto) {
        SkillGroup s = new SkillGroup();
        s.setCategory(dto.category());
        s.setContent(dto.content());
        return s;
    }

    private static ExperienceCompanyDto toDto(ExperienceCompany e) {
        return new ExperienceCompanyDto(
                e.getId(), e.getCompany(), e.getStartDate(), e.getEndDate(), e.getRoleTitle(), e.getContext(),
                e.getProjects().stream().map(DossierMapper::toDto).toList());
    }

    private static ExperienceCompany toEntity(ExperienceCompanyDto dto) {
        ExperienceCompany e = new ExperienceCompany();
        e.setCompany(dto.company());
        e.setStartDate(dto.startDate());
        e.setEndDate(dto.endDate());
        e.setRoleTitle(dto.roleTitle());
        e.setContext(dto.context());
        AtomicInteger pos = new AtomicInteger();
        emptyIfNull(dto.projects()).forEach(p -> {
            ExperienceProject project = toEntity(p);
            project.setPosition(pos.getAndIncrement());
            e.addProject(project);
        });
        return e;
    }

    private static ExperienceProjectDto toDto(ExperienceProject p) {
        return new ExperienceProjectDto(p.getId(), p.getName(), p.getContext(), p.getTeam(),
                p.getMethodology(), p.getTasks(), p.getTechnologies());
    }

    private static ExperienceProject toEntity(ExperienceProjectDto dto) {
        ExperienceProject p = new ExperienceProject();
        p.setName(dto.name());
        p.setContext(dto.context());
        p.setTeam(dto.team());
        p.setMethodology(dto.methodology());
        p.setTasks(dto.tasks());
        p.setTechnologies(dto.technologies());
        return p;
    }

    private static CertificationDto toDto(Certification c, UnaryOperator<String> imageUrlResolver) {
        return new CertificationDto(c.getId(), c.getName(), c.getIssuer(), c.getYear(), c.getLink(), imageUrlResolver.apply(c.getImageUrl()));
    }

    private static Certification toEntity(CertificationDto dto) {
        Certification c = new Certification();
        c.setName(dto.name());
        c.setIssuer(dto.issuer());
        c.setYear(dto.year());
        c.setLink(dto.link());
        c.setImageUrl(dto.imageUrl());
        return c;
    }

    private static EducationDto toDto(Education e, UnaryOperator<String> imageUrlResolver) {
        return new EducationDto(e.getId(), e.getSchool(), e.getDegree(), e.getYear(), e.getLink(), imageUrlResolver.apply(e.getImageUrl()));
    }

    private static Education toEntity(EducationDto dto) {
        Education e = new Education();
        e.setSchool(dto.school());
        e.setDegree(dto.degree());
        e.setYear(dto.year());
        e.setLink(dto.link());
        e.setImageUrl(dto.imageUrl());
        return e;
    }

    private static LanguageDto toDto(LanguageSkill l) {
        return new LanguageDto(l.getId(), l.getLanguage(), l.getLevel());
    }

    private static LanguageSkill toEntity(LanguageDto dto) {
        LanguageSkill l = new LanguageSkill();
        l.setLanguage(dto.language());
        l.setLevel(dto.level());
        return l;
    }

    private static ReferenceDto toDto(DossierReference r) {
        return new ReferenceDto(r.getId(), r.getName(), r.getEmail(), r.getPhone(), r.getDescription(), r.getLink());
    }

    private static DossierReference toEntity(ReferenceDto dto) {
        DossierReference r = new DossierReference();
        r.setName(dto.name());
        r.setEmail(dto.email());
        r.setPhone(dto.phone());
        r.setDescription(dto.description());
        r.setLink(dto.link());
        return r;
    }

    private static TestResultDto toDto(TestResult t, UnaryOperator<String> imageUrlResolver) {
        return new TestResultDto(t.getId(), t.getName(), t.getScore(), t.getDescription(), t.getLink(), imageUrlResolver.apply(t.getImageUrl()));
    }

    private static TestResult toEntity(TestResultDto dto) {
        TestResult t = new TestResult();
        t.setName(dto.name());
        t.setScore(dto.score());
        t.setDescription(dto.description());
        t.setLink(dto.link());
        t.setImageUrl(dto.imageUrl());
        return t;
    }
}
