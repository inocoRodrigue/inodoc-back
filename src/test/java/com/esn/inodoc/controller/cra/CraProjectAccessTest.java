package com.esn.inodoc.controller.cra;

import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.service.cra.CraProjectService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(CraProjectAccessTest.Config.class)
class CraProjectAccessTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CraProjectService craProjectService() { return mock(CraProjectService.class); }
        @Bean CraProjectController craProjectController(CraProjectService service) {
            return new CraProjectController(service);
        }
    }

    @jakarta.annotation.Resource CraProjectController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void financeManagerCanManageProjectsAndCollaborators() throws Exception {
        mvc.perform(get("/cra-projects")).andExpect(status().isOk());
        mvc.perform(get("/cra-projects/collaborators")).andExpect(status().isOk());
        mvc.perform(get("/cra-projects/available")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanManageProjects() throws Exception {
        mvc.perform(get("/cra-projects")).andExpect(status().isOk());
        mvc.perform(get("/cra-projects/available")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INTERNAL")
    void internalCollaboratorCanReadAvailableProjects() throws Exception {
        mvc.perform(get("/cra-projects/available")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BUSINESS_MANAGER")
    void businessManagerCanReadAvailableProjects() throws Exception {
        mvc.perform(get("/cra-projects/available")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BUSINESS_MANAGER")
    void businessManagerCannotManageCraProjects() throws Exception {
        mvc.perform(get("/cra-projects")).andExpect(status().isForbidden());
        mvc.perform(get("/cra-projects/collaborators")).andExpect(status().isForbidden());
    }
}