package com.esn.inodoc.controller;

import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(UserAccessControlTest.Config.class)
class UserAccessControlTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean UserController userController(UserService service) { return new UserController(service); }
    }

    @Autowired UserController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @WithMockUser(roles = "BUSINESS_MANAGER")
    void managerCannotListReadUpdateAssignRolesOrDeleteUsers() throws Exception {
        String user = "/users/" + UUID.randomUUID();
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(get(user)).andExpect(status().isForbidden());
        mvc.perform(put(user).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(user + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(user)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_MANAGER")
    void financeManagerCannotAdministerUsers() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isForbidden());
        mvc.perform(put("/users/" + UUID.randomUUID() + "/role")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanListAndMalformedRolePayloadReturns400() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isOk());
        mvc.perform(put("/users/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"UNKNOWN_ROLE\"]}"))
                .andExpect(status().isBadRequest());
    }
}
