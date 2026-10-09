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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

@Component
public class CareerDataInitializer implements ApplicationRunner {
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        JsonNode seed = readSeed();
        seedReferenceDocuments(seed);
        List<ReferenceDocument> references = referenceDocumentRepository.findAllByOrderByCategoryAscTitleAsc();
        seedCareerTemplate(seed.path("careerTemplate"), references);
        seedReviewForms(seed, references);
    }

    private void seedCareerTemplate(JsonNode data, List<ReferenceDocument> references) {
        CareerTemplate template = careerTemplateRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        JsonNode linkedSchema = bindReferenceLinks(data.path("entrySchema"), references);
        if (template != null) {
            String legacyTitle = data.path("legacyTitle").asText();
            if (template.getCreatedBy() == null && normalize(template.getTitle()).equals(normalize(legacyTitle))) {
                template.setTitle(data.path("title").asText());
                template.setDescription(data.path("description").asText());
                template.setEntrySchema(writeJson(linkedSchema));
                careerTemplateRepository.save(template);
            } else {
                String updatedSchema = writeJson(bindReferenceLinks(readJson(template.getEntrySchema()), references));
                if (!updatedSchema.equals(template.getEntrySchema())) {
                    template.setEntrySchema(updatedSchema);
                    careerTemplateRepository.save(template);
                }
            }
            return;
        }
        template = new CareerTemplate();
        template.setTitle(data.path("title").asText());
        template.setDescription(data.path("description").asText());
        template.setEntrySchema(writeJson(linkedSchema));
        careerTemplateRepository.save(template);
    }

    private void seedReviewForms(JsonNode seed, List<ReferenceDocument> references) {
        List<ReviewForm> existingForms = new ArrayList<>(reviewFormRepository.findAllByOrderByCreatedAtDesc());
        JsonNode schema = bindReferenceLinks(seed.path("reviewFormSchema"), references);
        for (JsonNode data : seed.path("reviewForms")) {
            String title = data.path("title").asText();
            String legacyTitle = data.path("legacyTitle").asText();
            ReviewForm current = existingForms.stream()
                    .filter(form -> normalize(form.getTitle()).equals(normalize(title)))
                    .findFirst().orElse(null);
            if (current != null) {
                String updatedSchema = writeJson(bindReferenceLinks(readJson(current.getSchema()), references));
                if (!updatedSchema.equals(current.getSchema())) {
                    current.setSchema(updatedSchema);
                    reviewFormRepository.save(current);
                }
                continue;
            }

            ReviewForm legacy = existingForms.stream()
                    .filter(form -> normalize(form.getTitle()).equals(normalize(legacyTitle)))
                    .findFirst().orElse(null);
            if (legacy != null && legacy.getCreatedBy() == null) {
                legacy.setTitle(title);
                legacy.setDescription(data.path("description").asText());
                legacy.setSchema(writeJson(schema));
                reviewFormRepository.save(legacy);
            } else if (legacy != null) {
                String updatedSchema = writeJson(bindReferenceLinks(readJson(legacy.getSchema()), references));
                if (!updatedSchema.equals(legacy.getSchema())) {
                    legacy.setSchema(updatedSchema);
                    reviewFormRepository.save(legacy);
                }
            } else {
                ReviewForm form = new ReviewForm();
                form.setTitle(title);
                form.setDescription(data.path("description").asText());
                form.setSchema(writeJson(schema));
                existingForms.add(reviewFormRepository.save(form));
            }
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
        List<ReferenceDocument> existingDocuments = new ArrayList<>(
            referenceDocumentRepository.findAllByOrderByCategoryAscTitleAsc());
        for (JsonNode data : seed.path("referenceDocuments")) {
            String title = data.path("title").asText();
            String legacyTitle = data.path("legacyTitle").asText();
            ReferenceDocument current = existingDocuments.stream()
                    .filter(document -> normalize(document.getTitle()).equals(normalize(title)))
                    .findFirst().orElse(null);
            if (current != null) continue;

            ReferenceDocument legacy = existingDocuments.stream()
                    .filter(document -> normalize(document.getTitle()).equals(normalize(legacyTitle)))
                    .findFirst().orElse(null);
                    if (legacy != null && legacy.getCreatedBy() == null) {
                legacy.setTitle(title);
                legacy.setDescription(data.path("description").asText());
                legacy.setCategory(data.path("category").asText());
                legacy.setContent(writeJson(data.path("content")));
                referenceDocumentRepository.save(legacy);
            } else {
                ReferenceDocument document = new ReferenceDocument();
                document.setTitle(title);
                document.setDescription(data.path("description").asText());
                document.setCategory(data.path("category").asText());
                document.setContent(writeJson(data.path("content")));
                existingDocuments.add(referenceDocumentRepository.save(document));
            }
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

    private JsonNode readJson(String value) {
        try {
            return mapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Career template data could not be parsed", ex);
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}