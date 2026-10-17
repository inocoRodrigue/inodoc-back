package com.esn.inodoc.controller;

import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.dto.company.CraFeatureStatus;
import com.esn.inodoc.service.CraFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(CraFeatureAccessTest.Config.class)
class CraFeatureAccessTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CraFeatureService craFeatureService() { return mock(CraFeatureService.class); }
        @Bean CraFeatureController craFeatureController(CraFeatureService service) {
            return new CraFeatureController(service);
        }
    }

    @jakarta.annotation.Resource CraFeatureController controller;
    @jakarta.annotation.Resource CraFeatureService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanEnableCra() throws Exception {
        when(service.setEnabled(true)).thenReturn(new CraFeatureStatus(true));

        mvc.perform(put("/cra-feature").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(service).setEnabled(true);
    }

    @Test
    @WithMockUser(roles = "INTERNAL")
    void collaboratorCannotChangeCraAvailability() throws Exception {
        mvc.perform(put("/cra-feature").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INTERNAL")
    void collaboratorCanReadCraAvailability() throws Exception {
        when(service.status()).thenReturn(new CraFeatureStatus(false));

        mvc.perform(get("/cra-feature")).andExpect(status().isOk());
    }
}