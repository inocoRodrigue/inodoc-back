package com.esn.inodoc.config;

import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.domain.review.ReferenceDocument;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        seedCareerTemplate(seed.path("careerTemplate"));
        seedReviewForms(seed);
        seedReferenceDocuments(seed);
    }

    private void seedCareerTemplate(JsonNode data) {
        CareerTemplate template = careerTemplateRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (template != null) {
            String legacyTitle = data.path("legacyTitle").asText();
                    if (template.getCreatedBy() == null && normalize(template.getTitle()).equals(normalize(legacyTitle))) {
                template.setTitle(data.path("title").asText());
                template.setDescription(data.path("description").asText());
                template.setEntrySchema(writeJson(data.path("entrySchema")));
                careerTemplateRepository.save(template);
            }
            return;
        }
        template = new CareerTemplate();
        template.setTitle(data.path("title").asText());
        template.setDescription(data.path("description").asText());
        template.setEntrySchema(writeJson(data.path("entrySchema")));
        careerTemplateRepository.save(template);
    }

    private void seedReviewForms(JsonNode seed) {
        List<ReviewForm> existingForms = new ArrayList<>(reviewFormRepository.findAllByOrderByCreatedAtDesc());
        JsonNode schema = seed.path("reviewFormSchema");
        for (JsonNode data : seed.path("reviewForms")) {
            String title = data.path("title").asText();
            String legacyTitle = data.path("legacyTitle").asText();
            ReviewForm current = existingForms.stream()
                    .filter(form -> normalize(form.getTitle()).equals(normalize(title)))
                    .findFirst().orElse(null);
            if (current != null) continue;

            ReviewForm legacy = existingForms.stream()
                    .filter(form -> normalize(form.getTitle()).equals(normalize(legacyTitle)))
                    .findFirst().orElse(null);
                    if (legacy != null && legacy.getCreatedBy() == null) {
                legacy.setTitle(title);
                legacy.setDescription(data.path("description").asText());
                legacy.setSchema(writeJson(schema));
                reviewFormRepository.save(legacy);
            } else {
                ReviewForm form = new ReviewForm();
                form.setTitle(title);
                form.setDescription(data.path("description").asText());
                form.setSchema(writeJson(schema));
                existingForms.add(reviewFormRepository.save(form));
            }
        }
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

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}