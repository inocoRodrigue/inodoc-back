package com.esn.inodoc.repo.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.cra.CraDayEntry;
import com.esn.inodoc.domain.cra.CraDeclaration;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.domain.cra.CraWorkLocation;
import com.esn.inodoc.dto.cra.CraValidatedFilter;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;

public final class CraDeclarationSpecifications {
    private CraDeclarationSpecifications() {}

    public static Specification<CraDeclaration> validated(CraValidatedFilter filter) {
        if ((filter.declaredFrom() != null && filter.declaredTo() != null && filter.declaredFrom().isAfter(filter.declaredTo()))
                || (filter.validatedFrom() != null && filter.validatedTo() != null && filter.validatedFrom().isAfter(filter.validatedTo()))
                || (filter.minDays() != null && filter.maxDays() != null && filter.minDays() > filter.maxDays())) {
            throw new BadRequestException("Invalid filter range");
        }
        ZoneId zone;
        CraWorkLocation location;
        try {
            zone = filter.timeZone() == null ? ZoneId.systemDefault() : ZoneId.of(filter.timeZone());
            location = filter.workLocation() == null || filter.workLocation().isBlank() || filter.workLocation().equals("UNSPECIFIED")
                    ? null : CraWorkLocation.valueOf(filter.workLocation());
        } catch (DateTimeException | IllegalArgumentException error) {
            throw new BadRequestException("Invalid timezone or work location");
        }
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("status"), CraDeclarationStatus.VALIDATED));
            if (filter.year() != null) predicates.add(builder.equal(root.get("year"), filter.year()));
            if (filter.month() != null) predicates.add(builder.equal(root.get("month"), filter.month()));
            if (filter.collaboratorId() != null) predicates.add(builder.equal(root.get("collaborator").get("id"), filter.collaboratorId()));
            var validator = root.join("validatedBy", JoinType.LEFT);
            Expression<String> validatorName = builder.concat(builder.concat(validator.get("firstName"), " "), validator.get("lastName"));
            if (filter.validator() != null && !filter.validator().isBlank()) predicates.add(builder.equal(validatorName, filter.validator()));
            if (filter.projectId() != null || (filter.workLocation() != null && !filter.workLocation().isBlank())) {
                var subquery = query.subquery(Integer.class);
                var day = subquery.from(CraDayEntry.class);
                var dayPredicates = new ArrayList<Predicate>();
                dayPredicates.add(builder.equal(day.get("declaration"), root));
                var primary = new ArrayList<Predicate>(); var afternoon = new ArrayList<Predicate>();
                afternoon.add(builder.equal(day.get("dayPart"), com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY));
                afternoon.add(builder.isNotNull(day.get("afternoonProject")));
                if (filter.projectId() != null) {
                    primary.add(builder.equal(day.get("project").get("id"), filter.projectId()));
                    afternoon.add(builder.equal(day.get("afternoonProject").get("id"), filter.projectId()));
                }
                if (filter.workLocation() != null && !filter.workLocation().isBlank()) {
                    primary.add(filter.workLocation().equals("UNSPECIFIED") ? builder.isNull(day.get("workLocation")) : builder.equal(day.get("workLocation"), location));
                    afternoon.add(filter.workLocation().equals("UNSPECIFIED") ? builder.isNull(day.get("afternoonWorkLocation")) : builder.equal(day.get("afternoonWorkLocation"), location));
                }
                dayPredicates.add(builder.or(builder.and(primary.toArray(Predicate[]::new)), builder.and(afternoon.toArray(Predicate[]::new))));
                subquery.select(builder.literal(1)).where(dayPredicates.toArray(Predicate[]::new));
                predicates.add(builder.exists(subquery));
            }
            if (filter.declaredFrom() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("declaredAt"), filter.declaredFrom().atStartOfDay(zone).toInstant()));
            if (filter.declaredTo() != null) predicates.add(builder.lessThan(root.get("declaredAt"), filter.declaredTo().plusDays(1).atStartOfDay(zone).toInstant()));
            if (filter.validatedFrom() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("validatedAt"), filter.validatedFrom().atStartOfDay(zone).toInstant()));
            if (filter.validatedTo() != null) predicates.add(builder.lessThan(root.get("validatedAt"), filter.validatedTo().plusDays(1).atStartOfDay(zone).toInstant()));
            if (filter.minDays() != null || filter.maxDays() != null) {
                var units = query.subquery(Double.class); var entry = units.from(CraDayEntry.class);
                units.select(builder.coalesce(builder.sum(builder.<Double>selectCase()
                        .when(builder.equal(entry.get("dayPart"), com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY), 1.0)
                        .otherwise(0.5)), 0.0)).where(builder.equal(entry.get("declaration"), root));
                if (filter.minDays() != null) predicates.add(builder.ge(units, filter.minDays()));
                if (filter.maxDays() != null) predicates.add(builder.le(units, filter.maxDays()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                Expression<String> person = builder.concat(builder.concat(root.get("collaborator").get("firstName"), " "), root.get("collaborator").get("lastName"));
                Expression<String> text = builder.concat(builder.concat(person, " "), root.get("collaborator").get("email"));
                text = builder.concat(builder.concat(text, " "), builder.coalesce(validatorName, ""));
                String normalized = Normalizer.normalize(filter.search(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
                for (String term : normalized.trim().split("\\s+")) {
                    String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                    var projectQuery = query.subquery(Integer.class);
                    var day = projectQuery.from(CraDayEntry.class);
                        var secondary = day.join("afternoonProject", JoinType.LEFT);
                        projectQuery.select(builder.literal(1)).where(builder.equal(day.get("declaration"), root), builder.or(
                            builder.like(normalize(builder, day.get("project").get("name")), pattern, '\\'),
                            builder.like(normalize(builder, builder.coalesce(secondary.get("name"), "")), pattern, '\\')));
                    predicates.add(builder.or(builder.like(normalize(builder, text), pattern, '\\'), builder.exists(projectQuery)));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Expression<String> normalize(jakarta.persistence.criteria.CriteriaBuilder builder, Expression<String> value) {
        return builder.function("translate", String.class, builder.lower(value),
                builder.literal("\u00e0\u00e2\u00e4\u00e9\u00e8\u00ea\u00eb\u00ee\u00ef\u00f4\u00f6\u00f9\u00fb\u00fc\u00ff\u00e7"),
                builder.literal("aaaeeeeiioouuuyc"));
    }
}