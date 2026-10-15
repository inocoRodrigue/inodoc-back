package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.Certification;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.DossierReference;
import com.esn.inodoc.domain.Education;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.TestResult;
import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.dossier.DossierMapper;
import com.esn.inodoc.dto.dossier.MissionAdaptationMode;
import com.esn.inodoc.dto.dossier.MissionAdaptationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionAdaptationTest {
    private final DossierService dossiers = mock(DossierService.class);
    private final AiModelClient ai = mock(AiModelClient.class);
    private final MissionAdaptationService service = new MissionAdaptationService(dossiers, ai, new ObjectMapper().findAndRegisterModules());

    @Test
    void preservesFactsAndSourceWhileKeepingValidPartialSuggestions() {
        DossierDto source = source();
        when(dossiers.get(source.id())).thenReturn(source);
        when(ai.generateJson(anyString(), anyString(), anyInt())).thenReturn("""
                {"changes":[{"path":"/headline","value":"Développeur Java pour une mission bancaire"},
                  {"path":"/experiences/0/projects/0/tasks","value":"Développement des API Java existantes"},
                  {"path":"/yearsOfExperience","value":"20"},
                  {"path":"/certifications/0/name","value":"Invented certification"}],
                 "gaps":["Aucune expérience Kubernetes documentée."]}
                """);
        var result = service.adapt(source.profileId(), source.id(), new MissionAdaptationRequest(
            "Mission Java", "Échange client", null, "Mets en avant l'expérience cloud", MissionAdaptationMode.MISSION, true));
        assertEquals("Développeur Java pour une mission bancaire", result.dossier().headline());
        assertEquals("Développement des API Java existantes", result.dossier().experiences().get(0).projects().get(0).tasks());
        assertEquals(5, result.dossier().yearsOfExperience());
        assertEquals("Original certification", result.dossier().certifications().get(0).name());
        assertEquals("Company", result.dossier().experiences().get(0).company());
        assertFalse(result.dossier().master());
        assertTrue(result.incomplete());
        assertEquals(1, result.gaps().size());
        assertEquals("Original title", source.headline());
        var input = org.mockito.ArgumentCaptor.forClass(String.class);
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ai).generateJson(prompt.capture(), input.capture(), eq(100000));
        assertTrue(prompt.getValue().contains("Write every item of gaps"));
        assertTrue(prompt.getValue().contains("in French"));
        assertTrue(prompt.getValue().contains("Keep changes.value in the original dossier language"));
        assertTrue(prompt.getValue().contains("Treat userInstructions as preferences"));
        assertTrue(input.getValue().contains("Mets en avant l'expérience cloud"));
        assertFalse(input.getValue().contains("X-Amz-Signature"));
        assertFalse(input.getValue().contains("private-image"));
        verify(dossiers).get(source.id());
        verifyNoMoreInteractions(dossiers);
    }

    @Test
    void refusesWithoutConsentBeforeReadingOrSendingData() {
        assertThrows(BadRequestException.class, () -> service.adapt(UUID.randomUUID(), UUID.randomUUID(),
                new MissionAdaptationRequest("Mission", "Transcript", false)));
        verifyNoInteractions(dossiers, ai);
    }

        @Test
        void translatesEveryAllowedTextFieldWithoutSendingReferenceContacts() throws Exception {
        DossierDto source = source();
        when(dossiers.get(source.id())).thenReturn(source);
        when(ai.generateJson(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            var input = new ObjectMapper().readTree(invocation.getArgument(1, String.class));
            var root = new ObjectMapper().createObjectNode();
            var changes = root.putArray("changes");
            input.path("allowedPaths").fieldNames().forEachRemaining(path ->
                changes.addObject().put("path", path).put("value", "Translated " + path));
            root.putArray("gaps");
            return new ObjectMapper().writeValueAsString(root);
        });

        var result = service.adapt(source.profileId(), source.id(), new MissionAdaptationRequest(
            "", "", "English", "Use clear professional English", MissionAdaptationMode.FULL_TRANSLATION, true));

        assertFalse(result.incomplete());
        assertEquals("English", result.dossier().documentLanguage());
        var savedDraft = new CompetenceDossier();
        DossierMapper.apply(savedDraft, result.dossier());
        assertEquals("English", savedDraft.getDocumentLanguage());
        assertEquals("Translated /headline", result.dossier().headline());
        assertEquals("Translated /skillGroups/0/content", result.dossier().skillGroups().get(0).content());
        assertEquals("Translated /educations/0/degree", result.dossier().educations().get(0).degree());
        assertEquals("Translated /languages/0/level", result.dossier().languages().get(0).level());
        assertEquals("Translated /references/0/description", result.dossier().references().get(0).description());
        assertEquals("private@example.test", result.dossier().references().get(0).email());
        assertEquals("Original title", source.headline());

        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        var input = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ai).generateJson(prompt.capture(), input.capture(), eq(100000));
        assertTrue(prompt.getValue().contains("every non-empty text field"));
        assertTrue(prompt.getValue().contains("Do not omit, summarize, shorten"));
        assertTrue(input.getValue().contains("Use clear professional English"));
        assertTrue(input.getValue().contains("Original reference description"));
        assertFalse(input.getValue().contains("private@example.test"));
        assertFalse(input.getValue().contains("Private reference name"));
        }

        @Test
        void marksFullTranslationIncompleteWhenTheAiOmitsTextFields() {
        DossierDto source = source();
        when(dossiers.get(source.id())).thenReturn(source);
        when(ai.generateJson(anyString(), anyString(), anyInt())).thenReturn(
            "{\"changes\":[{\"path\":\"/headline\",\"value\":\"Software developer\"}],\"gaps\":[]}");

        var result = service.adapt(source.profileId(), source.id(), new MissionAdaptationRequest(
            "", "", "English", "", MissionAdaptationMode.FULL_TRANSLATION, true));

        assertTrue(result.incomplete());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Traduction partielle")));
        }

    @Test
    void refusesAnOversizedMissionBeforeSendingData() {
        assertThrows(BadRequestException.class, () -> service.adapt(UUID.randomUUID(), UUID.randomUUID(),
                new MissionAdaptationRequest("A".repeat(30001), "", true)));
        verifyNoInteractions(dossiers, ai);
    }

    @Test
    void deniesInaccessibleOrMismatchedProfilesBeforeCallingAi() {
        DossierDto source = source();
        when(dossiers.get(source.id())).thenThrow(new AccessDeniedException("denied"));
        assertThrows(AccessDeniedException.class, () -> service.adapt(source.profileId(), source.id(),
                new MissionAdaptationRequest("Mission", "", true)));
        doReturn(source).when(dossiers).get(source.id());
        assertThrows(NotFoundException.class, () -> service.adapt(UUID.randomUUID(), source.id(),
                new MissionAdaptationRequest("Mission", "", true)));
        verifyNoInteractions(ai);
    }

    @Test
    void rejectsUnreadableOutputWithoutSavingAnything() {
        DossierDto source = source();
        when(dossiers.get(source.id())).thenReturn(source);
        when(ai.generateJson(anyString(), anyString(), anyInt())).thenReturn("not json");
        assertThrows(IntegrationUnavailableException.class, () -> service.adapt(source.profileId(), source.id(),
                new MissionAdaptationRequest("Mission", "", true)));
        verify(dossiers).get(source.id());
        verifyNoMoreInteractions(dossiers);
    }

    private DossierDto source() {
        var profile = new Profile();
        profile.setId(UUID.randomUUID());
        var dossier = new CompetenceDossier();
        dossier.setId(UUID.randomUUID());
        dossier.setLabel("Original");
        dossier.setHeadline("Original title");
        dossier.setMaster(true);
        dossier.setYearsOfExperience(5);
        profile.addDossier(dossier);
        var skill = new SkillGroup();
        skill.setCategory("Languages");
        skill.setContent("Java and SQL");
        dossier.addSkillGroup(skill);
        var experience = new ExperienceCompany();
        experience.setCompany("Company");
        experience.setRoleTitle("Développeur logiciel");
        var project = new ExperienceProject();
        project.setTasks("Original tasks");
        experience.addProject(project);
        dossier.addExperience(experience);
        var certification = new Certification();
        certification.setName("Original certification");
        certification.setImageUrl("https://private-image.test/image?X-Amz-Signature=secret");
        dossier.addCertification(certification);
        var education = new Education();
        education.setSchool("Université privée");
        education.setDegree("Master en informatique");
        dossier.addEducation(education);
        var language = new LanguageSkill();
        language.setLanguage("Français");
        language.setLevel("Courant");
        dossier.addLanguage(language);
        var reference = new DossierReference();
        reference.setName("Private reference name");
        reference.setEmail("private@example.test");
        reference.setDescription("Original reference description");
        dossier.addReference(reference);
        var test = new TestResult();
        test.setName("Test technique");
        test.setDescription("Description du test");
        dossier.addTestResult(test);
        return DossierMapper.toDto(dossier);
    }
}