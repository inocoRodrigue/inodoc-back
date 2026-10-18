package com.esn.inodoc.controller.cra;

import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.service.cra.CraDeclarationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(CraDeclarationAccessTest.Config.class)
class CraDeclarationAccessTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CraDeclarationService craDeclarationService() { return mock(CraDeclarationService.class); }
        @Bean CraDeclarationController craDeclarationController(CraDeclarationService service) {
            return new CraDeclarationController(service);
        }
    }

    @jakarta.annotation.Resource CraDeclarationController controller;
    @jakarta.annotation.Resource CraDeclarationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @WithMockUser(roles = "INTERNAL")
    void internalCollaboratorCanReadAndCreateOwnDeclaration() throws Exception {
        mvc.perform(get("/cra/current")).andExpect(status().isOk());
        mvc.perform(post("/cra/current")).andExpect(status().isCreated());
        mvc.perform(post("/cra/2026/4")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void financeManagerCanDeclareAndReview() throws Exception {
        mvc.perform(get("/cra/current")).andExpect(status().isOk());
        mvc.perform(post("/cra/current")).andExpect(status().isCreated());
        mvc.perform(get("/cra/review")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanDeclareAndReview() throws Exception {
        mvc.perform(get("/cra/current")).andExpect(status().isOk());
        mvc.perform(post("/cra/current")).andExpect(status().isCreated());
        mvc.perform(get("/cra/review")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BUSINESS_MANAGER")
    void businessManagerCanDeclareButCannotReview() throws Exception {
        mvc.perform(get("/cra/current")).andExpect(status().isOk());
        mvc.perform(post("/cra/current")).andExpect(status().isCreated());
        mvc.perform(get("/cra/review")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INTERNAL")
    void collaboratorCannotAccessTheValidationQueue() throws Exception {
        mvc.perform(get("/cra/review")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void validatedSearchBindsPaginationAndFilters() throws Exception {
        org.mockito.Mockito.clearInvocations(service);
        mvc.perform(get("/cra/validated").param("page", "400").param("size", "25")
                .param("year", "2025").param("month", "12").param("search", "Elodie")
                .param("validatedFrom", "2026-01-01").param("timeZone", "Europe/Paris"))
                .andExpect(status().isOk());
        var filter = org.mockito.ArgumentCaptor.forClass(com.esn.inodoc.dto.cra.CraValidatedFilter.class);
        org.mockito.Mockito.verify(service).listValidated(filter.capture(), org.mockito.ArgumentMatchers.eq(400),
                org.mockito.ArgumentMatchers.eq(25));
        org.junit.jupiter.api.Assertions.assertEquals(2025, filter.getValue().year());
        org.junit.jupiter.api.Assertions.assertEquals(12, filter.getValue().month());
        org.junit.jupiter.api.Assertions.assertEquals("Elodie", filter.getValue().search());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 1, 1), filter.getValue().validatedFrom());
        org.junit.jupiter.api.Assertions.assertEquals("Europe/Paris", filter.getValue().timeZone());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validatedSearchRejectsInvalidCriteriaAndHasNoCountEndpoint() throws Exception {
        mvc.perform(get("/cra/validated").param("month", "13")).andExpect(status().isBadRequest());
        mvc.perform(get("/cra/validated").param("year", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/cra/validated").param("minDays", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/cra/validated").param("collaboratorId", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/cra/validated/count")).andExpect(status().isNotFound());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "FINANCE_MANAGER", "INTERNAL", "BUSINESS_MANAGER"})
    void onlyReviewersCanListValidatedAndUnvalidate(String role) throws Exception {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(
                "reviewer", null, "ROLE_" + role));
        try {
            int expected = role.equals("ADMIN") || role.equals("FINANCE_MANAGER") ? 200 : 403;
            mvc.perform(get("/cra/validated")).andExpect(status().is(expected));
            mvc.perform(get("/cra/validated/filters")).andExpect(status().is(expected));
            mvc.perform(post("/cra/validated/" + java.util.UUID.randomUUID() + "/unvalidate"))
                    .andExpect(status().is(expected));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "FINANCE_MANAGER", "INTERNAL", "BUSINESS_MANAGER"})
    void onlyReviewersCanPreviewAndSaveCorrections(String role) throws Exception {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(
                "reviewer", null, "ROLE_" + role));
        try {
            int expectedStatus = role.equals("ADMIN") || role.equals("FINANCE_MANAGER") ? 200 : 403;
            String path = "/cra/review/" + java.util.UUID.randomUUID();
            mvc.perform(get(path + "/projects")).andExpect(status().is(expectedStatus));
            mvc.perform(post(path + "/preview").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"days\":[]}")).andExpect(status().is(expectedStatus));
            mvc.perform(put(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"days\":[]}")).andExpect(status().is(expectedStatus));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}