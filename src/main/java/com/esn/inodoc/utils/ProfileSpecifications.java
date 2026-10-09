package com.esn.inodoc.utils;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.domain.SkillGroup;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Building the advanced multi-criteria search on profiles.
 * The business content (skills, experiences, languages) is carried by the
 * dossier versions attached to the profile.
 */
public final class ProfileSpecifications {

    private ProfileSpecifications() {}

    public static Specification<Profile> build(ProfileSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(c.query())) {
                // Broad free-text search: every whitespace-separated term must be
                // found in at least one searchable field (AND across terms, OR
                // across fields), spanning identity, dossier content, skills,
                // technologies, experiences, projects and languages.
                Join<Profile, CompetenceDossier> dossiers = root.join("dossiers", JoinType.LEFT);
                Join<CompetenceDossier, SkillGroup> groups = dossiers.join("skillGroups", JoinType.LEFT);
                Join<CompetenceDossier, ExperienceCompany> experiences = dossiers.join("experiences", JoinType.LEFT);
                Join<ExperienceCompany, ExperienceProject> projects = experiences.join("projects", JoinType.LEFT);
                Join<CompetenceDossier, LanguageSkill> languages = dossiers.join("languages", JoinType.LEFT);

                Expression<?>[] fields = {
                        root.get("firstName"),
                        root.get("lastName"),
                        root.get("location"),
                        dossiers.get("headline"),
                        dossiers.get("summary"),
                        dossiers.get("expertiseDomain"),
                        groups.get("category"),
                        groups.get("content"),
                        experiences.get("company"),
                        experiences.get("roleTitle"),
                        experiences.get("context"),
                        projects.get("name"),
                        projects.get("context"),
                        projects.get("tasks"),
                        projects.get("technologies"),
                        languages.get("language"),
                };

                // Each search term (chip) is a comma-separated phrase; every term
                // must be found in at least one field (AND across terms).
                for (String rawTerm : c.query().split("[,;\\n]")) {
                    String term = rawTerm.trim().toLowerCase();
                    if (term.isEmpty()) {
                        continue;
                    }
                    String like = "%" + term + "%";
                    List<Predicate> anyField = new ArrayList<>();
                    for (Expression<?> field : fields) {
                        anyField.add(cb.like(cb.lower(field.as(String.class)), like));
                    }
                    predicates.add(cb.or(anyField.toArray(new Predicate[0])));
                }
                query.distinct(true);
            }

            if (StringUtils.hasText(c.location())) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("location"), "")),
                        "%" + c.location().toLowerCase() + "%"));
            }

            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }

            if (c.availability() != null) {
                predicates.add(cb.equal(root.get("availabilityStatus"), c.availability()));
            }

            if (c.availableFrom() != null) {
                // Profiles becoming available on or before the requested date.
                predicates.add(cb.and(
                        cb.isNotNull(root.get("availableFrom")),
                        cb.lessThanOrEqualTo(root.<LocalDate>get("availableFrom"), c.availableFrom())));
            }

            if (c.minYearsOfExperience() != null) {
                Join<Profile, CompetenceDossier> dossiers = root.join("dossiers", JoinType.LEFT);
                predicates.add(cb.greaterThanOrEqualTo(
                        cb.coalesce(dossiers.get("yearsOfExperience"), 0), c.minYearsOfExperience()));
                query.distinct(true);
            }

            if (StringUtils.hasText(c.skill())) {
                String like = "%" + c.skill().toLowerCase() + "%";
                Join<Profile, CompetenceDossier> dossiers = root.join("dossiers", JoinType.LEFT);
                Join<CompetenceDossier, SkillGroup> groups = dossiers.join("skillGroups", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(groups.get("category"), "")), like),
                        cb.like(cb.lower(cb.coalesce(groups.get("content"), "")), like)));
                query.distinct(true);
            }

            if (StringUtils.hasText(c.technology())) {
                String tech = "%" + c.technology().toLowerCase() + "%";
                Join<Profile, CompetenceDossier> dossiers = root.join("dossiers", JoinType.LEFT);
                Join<CompetenceDossier, SkillGroup> groups = dossiers.join("skillGroups", JoinType.LEFT);
                Join<CompetenceDossier, ExperienceCompany> experiences = dossiers.join("experiences", JoinType.LEFT);
                Join<ExperienceCompany, ExperienceProject> projects = experiences.join("projects", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(groups.get("content"), "")), tech),
                        cb.like(cb.lower(cb.coalesce(projects.get("technologies"), "")), tech)));
                query.distinct(true);
            }

            if (c.languages() != null) {
                for (String rawLang : c.languages()) {
                    String lang = rawLang == null ? "" : rawLang.trim().toLowerCase();
                    if (lang.isEmpty()) {
                        continue;
                    }
                    // A dedicated join per language enforces AND semantics: the
                    // profile must speak every selected language.
                    Join<Profile, CompetenceDossier> langDossiers = root.join("dossiers", JoinType.LEFT);
                    Join<CompetenceDossier, LanguageSkill> langJoin = langDossiers.join("languages", JoinType.LEFT);
                    predicates.add(cb.like(cb.lower(langJoin.get("language")), "%" + lang + "%"));
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record ProfileSearchCriteria(
            String query,
            String skill,
            String technology,
            String location,
            List<String> languages,
            AvailabilityStatus availability,
            ProfileStatus status,
            Integer minYearsOfExperience,
            LocalDate availableFrom) {
    }
}
