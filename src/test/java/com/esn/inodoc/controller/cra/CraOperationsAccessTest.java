package com.esn.inodoc.controller.cra;

import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.service.cra.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.YearMonth;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(CraOperationsAccessTest.Config.class)
class CraOperationsAccessTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CraRulesService rules() { return mock(CraRulesService.class); }
        @Bean CraAuditService audit() { return mock(CraAuditService.class); }
        @Bean CraDeclarationService declarations() { return mock(CraDeclarationService.class); }
        @Bean CraReportingService reporting() { return mock(CraReportingService.class); }
        @Bean CraOperationsController controller(CraRulesService rules, CraAuditService audit, CraDeclarationService declarations, CraReportingService reporting) { return new CraOperationsController(rules, audit, declarations, reporting); }
    }
    @jakarta.annotation.Resource CraOperationsController controller;
    @jakarta.annotation.Resource CraRulesService rules;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        doAnswer(call -> YearMonth.of(call.<Integer>getArgument(0), call.<Integer>getArgument(1))).when(rules).period(anyInt(), anyInt());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    UUID signIn(String name) {
        var user = new User(); user.setId(UUID.randomUUID()); user.setEmail("user@example.test"); user.setFirstName("User"); user.setLastName("Test");
        var role = new Role(); role.setName(RoleName.valueOf(name)); user.setRoles(Set.of(role));
        var principal = UserPrincipal.from(user); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return user.getId();
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "FINANCE_MANAGER", "BUSINESS_MANAGER", "INTERNAL"})
    void rulesMonitoringAndOwnerRoutesRespectRoles(String role) throws Exception {
        UUID owner = signIn(role); int reviewer = role.equals("ADMIN") || role.equals("FINANCE_MANAGER") ? 200 : 403;
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/cra/policy")).andExpect(status().isOk());
        mvc.perform(put("/cra/policy").contentType("application/json").content("{\"deadlineDay\":5,\"allowSelfApproval\":false,\"holidays\":[]}"))
                .andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
        mvc.perform(get("/cra/monitor/2026/1")).andExpect(status().is(reviewer));
        int statisticsAccess = reviewer == 200 || role.equals("BUSINESS_MANAGER") ? 200 : 403;
        mvc.perform(get("/cra/statistics/2026/1")).andExpect(status().is(statisticsAccess));
        mvc.perform(get("/cra/reminders")).andExpect(status().is(reviewer));
        mvc.perform(get("/cra/schedules/" + id)).andExpect(status().isNotFound());
        mvc.perform(put("/cra/schedules/" + id).contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/cra/periods/2026/1")).andExpect(status().isNotFound());
        mvc.perform(post("/cra/periods/2026/1/close").contentType("application/json").content("{\"reason\":\"Clôture\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/cra/periods/2026/1/open").contentType("application/json").content("{\"reason\":\"Ouverture\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/cra/review/" + id + "/reject").contentType("application/json").content("{\"reason\":\"Correction\"}"))
                .andExpect(status().is(reviewer));
        mvc.perform(get("/cra/calendar/2026/1").param("userId", owner.toString())).andExpect(status().isOk());
        mvc.perform(get("/cra/calendar/2026/1").param("userId", id)).andExpect(status().is(reviewer));
        mvc.perform(post("/cra/mine/2026/1")).andExpect(status().isOk());
        mvc.perform(put("/cra/mine/" + id).contentType("application/json").content("{\"days\":[]}")).andExpect(status().isOk());
        mvc.perform(post("/cra/mine/" + id + "/submit")).andExpect(status().isOk());
        mvc.perform(get("/cra/history/" + id)).andExpect(status().isOk());
    }
    @Test void blankRejectionAndInvalidPolicyAreRejected() throws Exception {
        signIn("ADMIN");
        mvc.perform(post("/cra/review/" + UUID.randomUUID() + "/reject").contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/cra/policy").contentType("application/json").content("{\"holidays\":null}")).andExpect(status().isBadRequest());
    }
}