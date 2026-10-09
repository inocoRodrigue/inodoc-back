package com.esn.inodoc.config;

import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.domain.review.ReferenceDocument;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            savedForms.add(form);
            return form;
        });
        when(referenceDocuments.findAllByOrderByCategoryAscTitleAsc())
                .thenAnswer(invocation -> List.copyOf(savedDocuments));
        when(referenceDocuments.save(any(ReferenceDocument.class))).thenAnswer(invocation -> {
            ReferenceDocument document = invocation.getArgument(0);
            savedDocuments.add(document);
            return document;
        });

        var initializer = new CareerDataInitializer(careerTemplates, reviewForms,
                referenceDocuments, new ObjectMapper());
        var arguments = new DefaultApplicationArguments();
        initializer.run(arguments);
        initializer.run(arguments);

        assertEquals("Carnet carrière INOCO", savedTemplate.get().getTitle());
        assertEquals(6, new ObjectMapper().readTree(savedTemplate.get().getEntrySchema())
                .path("sections").size());
        assertEquals(2, savedForms.size());
        assertEquals(4, savedDocuments.size());
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien annuel INOCO")));
        assertTrue(savedForms.stream().anyMatch(form -> form.getTitle().equals("Préparation à l'entretien semi-annuel INOCO")));
    }
}