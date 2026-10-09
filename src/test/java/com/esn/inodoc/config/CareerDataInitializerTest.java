package com.esn.inodoc.config;

import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.domain.review.ReferenceDocument;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerDataInitializerTest {
    @Test
    void seedsCareerDataOnceAndKeepsItOnSubsequentRuns() throws Exception {
        var careerTemplates = mock(CareerTemplateRepository.class);
        var reviewForms = mock(ReviewFormRepository.class);
        var referenceDocuments = mock(ReferenceDocumentRepository.class);
        var savedTemplate = new AtomicReference<CareerTemplate>();
        var savedForms = new ArrayList<ReviewForm>();
        var savedDocuments = new ArrayList<ReferenceDocument>();

        when(careerTemplates.findFirstByOrderByCreatedAtAsc())
                .thenAnswer(invocation -> Optional.ofNullable(savedTemplate.get()));
        when(careerTemplates.save(any(CareerTemplate.class))).thenAnswer(invocation -> {
            CareerTemplate template = invocation.getArgument(0);
            savedTemplate.set(template);
            return template;
        });
        when(reviewForms.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation -> List.copyOf(savedForms));
        when(reviewForms.save(any(ReviewForm.class))).thenAnswer(invocation -> {
            ReviewForm form = invocation.getArgument(0);
            if (!savedForms.contains(form)) savedForms.add(form);
            return form;
        });
        when(referenceDocuments.findAllByOrderByCategoryAscTitleAsc())
                .thenAnswer(invocation -> List.copyOf(savedDocuments));
        when(referenceDocuments.save(any(ReferenceDocument.class))).thenAnswer(invocation -> {
            ReferenceDocument document = invocation.getArgument(0);
            document.setId(UUID.randomUUID());
            savedDocuments.add(document);
            return document;
        });

        var initializer = new CareerDataInitializer(careerTemplates, reviewForms,
                referenceDocuments, new ObjectMapper());
        var arguments = new DefaultApplicationArguments();
        initializer.run(arguments);
        UUID managerId = UUID.randomUUID();
        savedTemplate.get().setCreatedBy(managerId);
        savedForms.forEach(form -> form.setCreatedBy(managerId));
        savedTemplate.get().setEntrySchema(removeAnchors(savedTemplate.get().getEntrySchema()));
        savedForms.forEach(form -> form.setSchema(removeAnchors(form.getSchema())));
        initializer.run(arguments);
        initializer.run(arguments);

        assertEquals("Carnet carrière INOCO", savedTemplate.get().getTitle());
        assertEquals(managerId, savedTemplate.get().getCreatedBy());
        assertEquals(6, new ObjectMapper().readTree(savedTemplate.get().getEntrySchema())
                .path("sections").size());
        assertEquals(2, savedForms.size());
        assertEquals(4, savedDocuments.size());
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien annuel INOCO")));
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien semi-annuel INOCO")));

        String valuableTaskReferenceId = savedDocuments.stream()
                .filter(document -> document.getTitle().equals("Tâches valorisables INOCO 2025"))
                .findFirst().orElseThrow().getId().toString();
        JsonNode careerSchema = new ObjectMapper().readTree(savedTemplate.get().getEntrySchema());
        assertEquals(valuableTaskReferenceId, field(careerSchema, "valuable-tasks-reference-anchor")
                .path("referenceId").asText());
        for (ReviewForm form : savedForms) {
            JsonNode reviewSchema = new ObjectMapper().readTree(form.getSchema());
            assertEquals(valuableTaskReferenceId, field(reviewSchema, "valuable-tasks-reference-anchor")
                    .path("referenceId").asText());
            assertEquals(2, anchorCount(reviewSchema));
        }
        assertEquals(2, anchorCount(careerSchema));
    }

    private JsonNode field(JsonNode schema, String id) {
        for (JsonNode section : schema.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                if (id.equals(field.path("id").asText())) return field;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private int anchorCount(JsonNode schema) {
        int count = 0;
        for (JsonNode section : schema.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                if ("ANCHOR".equals(field.path("type").asText())) count++;
            }
        }
        return count;
    }

    private String removeAnchors(String schemaText) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode schema = mapper.readTree(schemaText);
            for (JsonNode section : schema.path("sections")) {
                if (!(section.path("fields") instanceof com.fasterxml.jackson.databind.node.ArrayNode fields)) continue;
                for (int index = fields.size() - 1; index >= 0; index--) {
                    if ("ANCHOR".equals(fields.get(index).path("type").asText())) fields.remove(index);
                }
            }
            return mapper.writeValueAsString(schema);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}