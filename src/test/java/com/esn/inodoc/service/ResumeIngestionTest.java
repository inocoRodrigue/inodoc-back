package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeIngestionTest {
    @Test
    void parserNormalizesCollectionsAndPreventsMasterAssignment() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new ResumeIngestionService(new ResumeTextExtractor(),
                    new ObjectMapper().findAndRegisterModules(), factory.getValidator(), "", "", "");
            var result = service.parse("""
                    {"profile":{"firstName":"Test"}, "dossier":{"label":"CV","master":true,
                    "experiences":[{"company":"Test company","projects":null}]}}
                    """);
            assertEquals("Test", result.profile().firstName());
            assertFalse(result.dossier().master());
            assertTrue(result.dossier().skillGroups().isEmpty());
            assertTrue(result.dossier().experiences().get(0).projects().isEmpty());
            var partial = service.parse("""
                    {"profile":{}, "dossier":{"label":"CV","yearsOfExperience":-5}}
                    """);
            assertNull(partial.dossier().yearsOfExperience());
            assertTrue(partial.incomplete());
            assertFalse(partial.warnings().isEmpty());
            var invalidEducation = service.parse("""
                    {"profile":{}, "dossier":{"label":"CV","educations":[{"school":null}]}}
                    """);
            assertTrue(invalidEducation.dossier().educations().isEmpty());
            assertTrue(invalidEducation.incomplete());
            assertThrows(IntegrationUnavailableException.class, () -> service.parse("not json"));
        }
    }

        @Test
        void ingestionPromptRequiresDistinctTasksOnSeparateLines() {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var extractor = mock(ResumeTextExtractor.class);
                        var ai = mock(AiModelClient.class);
                        when(extractor.extract(any())).thenReturn("CV source text");
                        when(ai.generateJson(anyString(), anyString(), anyInt())).thenReturn(
                                        "{\"profile\":{},\"dossier\":{\"label\":\"CV\"}}");
                        var service = new ResumeIngestionService(extractor, new ObjectMapper(), factory.getValidator(), ai);

                        service.ingest(new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[]{1}));

                        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
                        verify(ai).generateJson(prompt.capture(), eq("CV source text"), eq(150000));
                            assertTrue(prompt.getValue().contains("tasks MUST be a JSON array of strings"));
                            assertTrue(prompt.getValue().contains("one distinct task or responsibility per array element"));
                }
        }

        @Test
        void parserReturnsSupportedSuggestionsAndRejectsUnknownFields() {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var service = new ResumeIngestionService(new ResumeTextExtractor(),
                                        new ObjectMapper().findAndRegisterModules(), factory.getValidator(), "", "", "");
                        var result = service.parse("""
                                        {"profile":{},"dossier":{"label":"CV"},"suggestions":[
                                          {"type":"IMPROVEMENT","fieldPath":"dossier.summary",
                                           "currentValue":"Developer","proposedValue":"Java developer",
                                           "rationale":"The CV lists Java experience."}]}
                                        """);
                        assertEquals(1, result.suggestions().size());
                        assertEquals("dossier.summary", result.suggestions().get(0).fieldPath());
                                                                                                var partial = service.parse("""
                                        {"profile":{},"dossier":{"label":"CV"},"suggestions":[
                                          {"type":"IMPROVEMENT","fieldPath":"dossier.admin",
                                           "proposedValue":"unexpected","rationale":"unsupported field"}]}
                                                                                                                                                                """);
                                                                                                assertTrue(partial.suggestions().isEmpty());
                                                                                                assertTrue(partial.incomplete());
                }
        }

                @Test
                void retainsValidFieldsAndNestedEntriesWhenOtherValuesAreInvalid() {
                                try (var factory = Validation.buildDefaultValidatorFactory()) {
                                                var service = new ResumeIngestionService(new ResumeTextExtractor(),
                                                                                new ObjectMapper().findAndRegisterModules(), factory.getValidator(), "", "", "");
                                                var result = service.parse("""
                                                                                {"profile":{"firstName":"Camille","email":"not-an-email","phone":123},
                                                                                 "dossier":{"headline":"Java developer","yearsOfExperience":2.5,
                                                                                         "experiences":[{"company":"Company","startDate":"bad-date",
                                                                                                 "projects":[{"name":"Project","tasks":["Designed APIs","Reviewed code","Deployed services"]},42]},42],
                                                                                         "educations":[{"school":"School","year":"2025"},{"school":null}],
                                                                                         "languages":"wrong type"},
                                                                                 "suggestions":[
                                                                                         {"type":"IMPROVEMENT","fieldPath":"profile.email","proposedValue":"invalid",
                                                                                                "rationale":"Invalid email must not be accepted."},
                                                                                         {"type":"IMPROVEMENT","fieldPath":"dossier.headline","proposedValue":"Java expert",
                                                                                                "rationale":"Supported by the CV."}]}
                                                                                """);
                                                assertEquals("Camille", result.profile().firstName());
                                                assertNull(result.profile().email());
                                                assertNull(result.profile().phone());
                                                assertNull(result.dossier().yearsOfExperience());
                                                assertEquals("Java developer", result.dossier().headline());
                                                assertEquals(1, result.dossier().experiences().size());
                                                assertNull(result.dossier().experiences().get(0).startDate());
                                                assertEquals(1, result.dossier().experiences().get(0).projects().size());
                                                assertEquals("Designed APIs\nReviewed code\nDeployed services",
                                                        result.dossier().experiences().get(0).projects().get(0).tasks());
                                                assertEquals(1, result.dossier().educations().size());
                                                assertTrue(result.dossier().languages().isEmpty());
                                                assertEquals(1, result.suggestions().size());
                                                assertEquals("dossier.headline", result.suggestions().get(0).fieldPath());
                                                assertTrue(result.incomplete());
                                                assertFalse(result.warnings().isEmpty());
                                }
                }

    @Test
    void missingConfigurationDoesNotSendData() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new ResumeIngestionService(new ResumeTextExtractor(),
                    new ObjectMapper(), factory.getValidator(), "https://example.test/v1", "", "test");
            assertThrows(IntegrationUnavailableException.class, () -> service.ingest(
                    new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1})));
        }
    }

        @Test
        void dropsOversizedAndUnknownFieldsWithoutRejectingValidData() {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var mapper = new ObjectMapper().findAndRegisterModules();
                        var service = new ResumeIngestionService(new ResumeTextExtractor(), mapper,
                                        factory.getValidator(), "", "", "");
                        var root = mapper.createObjectNode();
                        root.putObject("profile").put("firstName", "A".repeat(101)).put("lastName", "Test");
                        root.putObject("dossier").put("headline", "Valid title").put("summary", "A".repeat(5001))
                                        .put("yearsOfExperience", 100).put("admin", true);
                        var result = service.parse(root.toString());
                        assertNull(result.profile().firstName());
                        assertEquals("Test", result.profile().lastName());
                        assertEquals("Valid title", result.dossier().headline());
                        assertNull(result.dossier().summary());
                        assertNull(result.dossier().yearsOfExperience());
                        assertTrue(result.incomplete());
                        assertTrue(factory.getValidator().validate(result).isEmpty());
                }
        }

        @Test
        void normalizesLegacyBulletTaskTextIntoOneTaskPerLine() {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var service = new ResumeIngestionService(new ResumeTextExtractor(),
                                new ObjectMapper().findAndRegisterModules(), factory.getValidator(), "", "", "");
                        var result = service.parse("""
                                {"profile":{},"dossier":{"label":"CV","experiences":[{"company":"Company",
                                  "projects":[{"name":"Project","tasks":"• Design APIs • Review code • Deploy services"}]}]}}
                                """);

                        assertEquals("Design APIs\\nReview code\\nDeploy services",
                                result.dossier().experiences().get(0).projects().get(0).tasks());
                }
        }

        @Test
        void preservesValidEmptyCollectionSuggestionsAndPartiallyValidCollections() {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                        var service = new ResumeIngestionService(new ResumeTextExtractor(),
                                        new ObjectMapper().findAndRegisterModules(), factory.getValidator(), "", "", "");
                        var result = service.parse("""
                                        {"profile":{},"dossier":{"label":"CV"},"suggestions":[
                                          {"type":"CORRECTION","fieldPath":"dossier.references","proposedValue":[],
                                           "rationale":"Remove unsupported references."},
                                          {"type":"ADJUSTMENT","fieldPath":"dossier.educations",
                                           "proposedValue":[{"school":"Valid school"},{"school":null}],
                                           "rationale":"Use the education supported by the CV."}]}
                                        """);
                        assertEquals(2, result.suggestions().size());
                        assertTrue(result.suggestions().get(0).proposedValue().isEmpty());
                        assertEquals(1, result.suggestions().get(1).proposedValue().size());
                        assertEquals("Valid school", result.suggestions().get(1).proposedValue().get(0).path("school").asText());
                        assertTrue(result.incomplete());
                }
        }

    @Test
    void rejectsUnsupportedEmptyOversizeAndUnreadableDocuments() {
        var extractor = new ResumeTextExtractor();
        assertThrows(BadRequestException.class, () -> extractor.extract(
                new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1})));
        assertThrows(BadRequestException.class, () -> extractor.extract(
                new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0])));
        assertThrows(BadRequestException.class, () -> extractor.extract(
                new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])));
        assertThrows(BadRequestException.class, () -> extractor.extract(
                new MockMultipartFile("file", "test.pdf", "application/pdf", "not a PDF".getBytes())));
    }

    @Test
    void extractsDocxTextLocally() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var output = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Synthetic Java developer resume");
            document.write(output);
            var text = new ResumeTextExtractor().extract(new MockMultipartFile(
                    "file", "synthetic.docx", "application/octet-stream", output.toByteArray()));
            assertTrue(text.contains("Synthetic Java developer"));
        }
    }

    @Test
    void callsConfiguredCompatibleApiWithStructuredResponse() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var extractor = org.mockito.Mockito.mock(ResumeTextExtractor.class);
            var file = new MockMultipartFile("file", "synthetic.pdf", "application/pdf", new byte[]{1});
            org.mockito.Mockito.when(extractor.extract(file)).thenReturn("Synthetic resume");
            var builder = org.springframework.web.client.RestClient.builder();
            var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
            var mapper = new ObjectMapper().findAndRegisterModules();
            String content = "{\"profile\":{\"firstName\":\"Test\"},\"dossier\":{\"label\":\"CV\"}}";
            String response = mapper.writeValueAsString(java.util.Map.of("choices",
                    java.util.List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
            server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .requestTo("https://ai.example.test/v1/chat/completions"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .header("Authorization", "Bearer synthetic-test-key"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .jsonPath("$.response_format.type").value("json_object"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("write every suggestion rationale in French")))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("write proposedValue in French")))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Keep CV content in the source CV language")))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                            .jsonPath("$.messages[1].content").value("Synthetic resume"))
                    .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                            .withSuccess(response, org.springframework.http.MediaType.APPLICATION_JSON));
            var service = new ResumeIngestionService(extractor, mapper, factory.getValidator(),
                    "https://ai.example.test/v1", "synthetic-test-key", "test-model", builder.build());
            assertEquals("Test", service.ingest(file).profile().firstName());
            server.verify();
        }
    }

    @Test
    void retriesTemporaryProviderFailureOnce() {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("https://ai.example.test/v1/chat/completions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess("""
                                {"choices":[{"message":{"content":"{\\"ok\\":true}"}}]}
                                """, org.springframework.http.MediaType.APPLICATION_JSON));

        var client = new AiModelClient("https://ai.example.test/v1", "synthetic-test-key",
                "test-model", builder.build());

        assertEquals("{\"ok\":true}", client.generateJson("system prompt", "test input", 100));
        server.verify();
    }

        @Test
        void reviewGenerationPromptsKeepReaderFacingTextInFrench() {
                var extractor = org.mockito.Mockito.mock(ResumeTextExtractor.class);
                var ai = org.mockito.Mockito.mock(AiModelClient.class);
                var file = new MockMultipartFile("file", "synthetic.pdf", "application/pdf", new byte[]{1});
                org.mockito.Mockito.when(extractor.extract(file)).thenReturn("English source document");
                org.mockito.Mockito.when(ai.generateJson(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                                .thenReturn("{\"title\":\"Document\",\"sections\":[],\"blocks\":[]}");
                var service = new com.esn.inodoc.service.review.ReviewGenerationService(extractor, new ObjectMapper(), ai);
                service.generate(file, "FORM");
                service.generate(file, "REFERENCE");
                var prompts = org.mockito.ArgumentCaptor.forClass(String.class);
                org.mockito.Mockito.verify(ai, org.mockito.Mockito.times(2)).generateJson(prompts.capture(),
                                org.mockito.ArgumentMatchers.eq("English source document"), org.mockito.ArgumentMatchers.eq(200000));
                assertTrue(prompts.getAllValues().get(0).contains("in French, even when the source document is English"));
                assertTrue(prompts.getAllValues().get(1).contains("must be in French, even when the source is English"));
        }
}
