package com.esn.inodoc.config;

import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.domain.review.ReferenceDocument;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Locale;
import java.util.List;

@Component
public class CareerDataInitializer {
    private static final String SEED_RESOURCE = "data/career-initialization.json";

    private final CareerTemplateRepository careerTemplateRepository;
    private final ReviewFormRepository reviewFormRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final ObjectMapper mapper;

    public CareerDataInitializer(CareerTemplateRepository careerTemplateRepository,
                                 ReviewFormRepository reviewFormRepository,
                                 ReferenceDocumentRepository referenceDocumentRepository,
                                 ObjectMapper mapper) {
        this.careerTemplateRepository = careerTemplateRepository;
        this.reviewFormRepository = reviewFormRepository;
        this.referenceDocumentRepository = referenceDocumentRepository;
        this.mapper = mapper;
    }

    @Transactional
    public void initialize() {
        if (careerTemplateRepository.count() > 0 || reviewFormRepository.count() > 0
                || referenceDocumentRepository.count() > 0) return;
        JsonNode seed = readSeed();
        seedReferenceDocuments(seed);
        List<ReferenceDocument> references = referenceDocumentRepository.findAllByOrderByCategoryAscTitleAsc();
        seedCareerTemplate(seed.path("careerTemplate"), references);
        seedReviewForms(seed, references);
    }

    private void seedCareerTemplate(JsonNode data, List<ReferenceDocument> references) {
        JsonNode linkedSchema = bindReferenceLinks(data.path("entrySchema"), references);
        CareerTemplate template = new CareerTemplate();
        template.setTitle(data.path("title").asText());
        template.setDescription(data.path("description").asText());
        template.setEntrySchema(writeJson(linkedSchema));
        careerTemplateRepository.save(template);
    }

    private void seedReviewForms(JsonNode seed, List<ReferenceDocument> references) {
        JsonNode schema = bindReferenceLinks(seed.path("reviewFormSchema"), references);
        for (JsonNode data : seed.path("reviewForms")) {
            ReviewForm form = new ReviewForm();
            form.setTitle(data.path("title").asText());
            form.setDescription(data.path("description").asText());
            form.setSchema(writeJson(schema));
            reviewFormRepository.save(form);
        }
    }

    private JsonNode bindReferenceLinks(JsonNode source, List<ReferenceDocument> references) {
        ObjectNode schema = source.deepCopy();
        String badgeId = referenceId(references, "Référentiel des badges et tâches attendues INOCO 2026");
        String valuableTaskId = referenceId(references, "Tâches valorisables INOCO 2025");
        JsonNode sections = schema.path("sections");
        if (!sections.isArray()) return schema;

        for (JsonNode sectionNode : sections) {
            if (!(sectionNode instanceof ObjectNode section) || !section.path("fields").isArray()) continue;
            ArrayNode fields = (ArrayNode) section.path("fields");
            boolean hasBadgeAnchor = hasField(fields, "badge-reference-anchor");
            boolean hasValuableTaskAnchor = hasField(fields, "valuable-tasks-reference-anchor");
            ArrayNode linkedFields = mapper.createArrayNode();
            for (JsonNode fieldNode : fields) {
                linkedFields.add(fieldNode);
                String fieldId = fieldNode.path("id").asText();
                if ("badgeEvidence".equals(fieldId) && badgeId != null && !hasBadgeAnchor) {
                    linkedFields.add(anchor("badge-reference-anchor", "Consulter les attendus du badge", badgeId));
                    hasBadgeAnchor = true;
                }
                if (("valuableTaskEvidence".equals(fieldId) || "valuableTasks".equals(fieldId))
                        && valuableTaskId != null && !hasValuableTaskAnchor) {
                    linkedFields.add(anchor("valuable-tasks-reference-anchor",
                            "Consulter les tâches valorisables et leur barème", valuableTaskId));
                    hasValuableTaskAnchor = true;
                }
            }
            section.set("fields", linkedFields);
        }
        return schema;
    }

    private boolean hasField(ArrayNode fields, String id) {
        for (JsonNode field : fields) {
            if (id.equals(field.path("id").asText())) return true;
        }
        return false;
    }

    private ObjectNode anchor(String id, String label, String referenceId) {
        ObjectNode anchor = mapper.createObjectNode();
        anchor.put("id", id);
        anchor.put("type", "ANCHOR");
        anchor.put("label", label);
        anchor.put("referenceId", referenceId);
        return anchor;
    }

    private String referenceId(List<ReferenceDocument> references, String title) {
        return references.stream()
                .filter(reference -> normalize(reference.getTitle()).equals(normalize(title)))
                .map(reference -> reference.getId() == null ? null : reference.getId().toString())
                .filter(id -> id != null)
                .findFirst()
                .orElse(null);
    }

    private void seedReferenceDocuments(JsonNode seed) {
        for (JsonNode data : seed.path("referenceDocuments")) {
            ReferenceDocument document = new ReferenceDocument();
            document.setTitle(data.path("title").asText());
            document.setDescription(data.path("description").asText());
            document.setCategory(data.path("category").asText());
            document.setContent(writeJson(data.path("content")));
            referenceDocumentRepository.save(document);
        }
    }

    private JsonNode readSeed() {
        try (var input = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            JsonNode seed = mapper.readTree(input);
            if (seed == null || !seed.path("careerTemplate").path("entrySchema").path("sections").isArray()
                    || !seed.path("reviewFormSchema").path("sections").isArray()
                    || !seed.path("reviewForms").isArray() || !seed.path("referenceDocuments").isArray()) {
                throw new IllegalStateException("Career initialization data has an invalid structure");
            }
            return seed;
        } catch (IOException ex) {
            throw new IllegalStateException("Career initialization data could not be loaded", ex);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Career initialization data could not be serialized", ex);
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}