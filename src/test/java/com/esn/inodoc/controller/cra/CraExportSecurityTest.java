package com.esn.inodoc.controller.cra;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.config.SecurityConfig;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraDeclarationStatus;
import com.esn.inodoc.dto.cra.CraDeclarationDto;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.JwtAuthenticationFilter;
import com.esn.inodoc.security.JwtService;
import com.esn.inodoc.security.RestAccessDeniedHandler;
import com.esn.inodoc.security.RestAuthenticationEntryPoint;
import com.esn.inodoc.service.CraFeatureService;
import com.esn.inodoc.service.cra.CraAuditService;
import com.esn.inodoc.service.cra.CraDeclarationService;
import com.esn.inodoc.service.cra.CraExportService;
import com.esn.inodoc.service.cra.CraRulesService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CraExportController.class, properties = "inodoc.cors.allowed-origins=https://front.example.test")
@EnableConfigurationProperties(AppProperties.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CraExportSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean CraFeatureService feature;
    @MockitoBean CraExportService exports;
    @MockitoBean CraDeclarationService declarations;
    @MockitoBean CraRulesService rules;
    @MockitoBean CraAuditService audit;
    private static final byte[] FILE = "export-file-content".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setup() throws Exception {
        when(feature.isEnabled()).thenReturn(true);
        when(rules.period(2026, 10)).thenReturn(YearMonth.of(2026, 10));
        when(exports.mime(anyString())).thenAnswer(call -> switch (call.<String>getArgument(0)) {
            case "csv" -> "text/csv;charset=UTF-8";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/pdf";
        });
        doAnswer(call -> { call.getArgument(4, OutputStream.class).write(FILE); return null; })
                .when(exports).writePeriod(anyInt(), anyInt(), anyString(), anyString(), any(OutputStream.class));
        doAnswer(call -> { call.getArgument(3, OutputStream.class).write(FILE); return null; })
                .when(exports).write(any(), anyString(), anyString(), any(OutputStream.class));
        doAnswer(call -> { call.getArgument(2, OutputStream.class).write(FILE); return null; })
            .when(exports).writeOwn(any(), anyString(), any(OutputStream.class), anyBoolean());
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private User authenticate(String roleName) {
        User user = new User(); user.setId(UUID.randomUUID()); user.setEmail("user@example.test");
        user.setFirstName("User"); user.setLastName("Test");
        Role role = new Role(); role.setName(RoleName.valueOf(roleName)); user.setRoles(Set.of(role));
        Claims claims = mock(Claims.class);
        @SuppressWarnings("unchecked") Jws<Claims> signed = mock(Jws.class);
        when(claims.getSubject()).thenReturn(user.getId().toString());
        when(signed.getPayload()).thenReturn(claims); when(jwt.parse("export-token")).thenReturn(signed);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @ParameterizedTest
        @CsvSource({"ADMIN,csv", "ADMIN,xlsx", "ADMIN,pdf", "FINANCE_MANAGER,csv", "FINANCE_MANAGER,xlsx", "FINANCE_MANAGER,pdf",
            "BUSINESS_MANAGER,csv", "BUSINESS_MANAGER,xlsx", "BUSINESS_MANAGER,pdf"})
    void authorizedMonthlyExportSurvivesAsyncRedispatch(String role, String format) throws Exception {
        authenticate(role);
        var initial = mvc.perform(get("/cra/exports/2026/10").param("format", format)
                        .header("Authorization", "Bearer export-token"))
                .andExpect(request().asyncStarted()).andReturn();
        SecurityContextHolder.clearContext();

        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"CRA-2026-10." + format + "\""))
                .andExpect(content().bytes(FILE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "FINANCE_MANAGER", "BUSINESS_MANAGER", "INTERNAL"})
    void personalExportSurvivesAsyncRedispatch(String role) throws Exception {
        User owner = authenticate(role); UUID id = UUID.randomUUID();
        when(declarations.getMine(id)).thenReturn(new CraDeclarationDto(id, owner.getId(), "User Test", owner.getEmail(),
                2026, 10, CraDeclarationStatus.DECLARED, null, null, null, List.of(), null));
        var initial = mvc.perform(get("/cra/mine/" + id + "/export").header("Authorization", "Bearer export-token"))
                .andExpect(request().asyncStarted()).andReturn();
        SecurityContextHolder.clearContext();

        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andExpect(content().bytes(FILE));
        verify(declarations).getMine(id);
        verify(exports).writeOwn(any(), eq("pdf"), any(OutputStream.class), eq(!role.equals("INTERNAL")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL"})
    void unauthorizedRolesCannotStartMonthlyExports(String role) throws Exception {
        authenticate(role);
        mvc.perform(get("/cra/exports/2026/10").header("Authorization", "Bearer export-token"))
                .andExpect(status().isForbidden()).andExpect(request().asyncNotStarted());
        verifyNoInteractions(exports);
    }

    @Test
    void anonymousRequestsCannotStartExports() throws Exception {
        mvc.perform(get("/cra/exports/2026/10")).andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted());
        verifyNoInteractions(exports);
    }
}