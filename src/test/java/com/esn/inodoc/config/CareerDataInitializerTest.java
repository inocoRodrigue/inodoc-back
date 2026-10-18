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

import java.util.ArrayList;
import java.util.List;
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

        when(careerTemplates.count()).thenAnswer(invocation -> savedTemplate.get() == null ? 0L : 1L);
        when(careerTemplates.save(any(CareerTemplate.class))).thenAnswer(invocation -> {
            CareerTemplate template = invocation.getArgument(0);
            savedTemplate.set(template);
            return template;
        });
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
        initializer.initialize();
        assertEquals(2, anchorCount(new ObjectMapper().readTree(savedTemplate.get().getEntrySchema())));
        UUID managerId = UUID.randomUUID();
        savedTemplate.get().setCreatedBy(managerId);
        savedForms.forEach(form -> form.setCreatedBy(managerId));
        savedTemplate.get().setEntrySchema(removeAnchors(savedTemplate.get().getEntrySchema()));
        savedForms.forEach(form -> form.setSchema(removeAnchors(form.getSchema())));
        String customizedTemplate = savedTemplate.get().getEntrySchema();
        initializer.initialize();
        initializer.initialize();
        assertEquals(customizedTemplate, savedTemplate.get().getEntrySchema());

        assertEquals("Carnet carrière INOCO", savedTemplate.get().getTitle());
        assertEquals(managerId, savedTemplate.get().getCreatedBy());
        assertEquals(6, new ObjectMapper().readTree(savedTemplate.get().getEntrySchema())
                .path("sections").size());
        assertEquals(2, savedForms.size());
        assertEquals(4, savedDocuments.size());
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien annuel INOCO")));
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien semi-annuel INOCO")));

        JsonNode careerSchema = new ObjectMapper().readTree(savedTemplate.get().getEntrySchema());
        for (ReviewForm form : savedForms) {
            JsonNode reviewSchema = new ObjectMapper().readTree(form.getSchema());
            assertEquals(0, anchorCount(reviewSchema));
        }
        assertEquals(0, anchorCount(careerSchema));
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