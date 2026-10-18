package com.esn.inodoc.config;

import com.esn.inodoc.security.JwtAuthenticationFilter;
import com.esn.inodoc.security.JwtService;
import com.esn.inodoc.service.CraFeatureService;
import com.esn.inodoc.security.RestAccessDeniedHandler;
import com.esn.inodoc.security.RestAuthenticationEntryPoint;
import com.esn.inodoc.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = {SecurityBoundaryTest.PublicProbe.class,
    com.esn.inodoc.controller.ApplicationBackupController.class},
    properties = "inodoc.cors.allowed-origins=https://front.example.test")
@EnableConfigurationProperties(AppProperties.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    SecurityBoundaryTest.PublicProbe.class})
class SecurityBoundaryTest {
    @MockitoBean JwtService jwtService;
    @MockitoBean CraFeatureService craFeatureService;
    @MockitoBean UserRepository users;
    @MockitoBean com.esn.inodoc.service.ApplicationBackupService backups;
    @MockitoBean com.esn.inodoc.service.ApplicationResetConfirmationService resetConfirmations;
    @Autowired MockMvc mvc;

    @RestController
    static class PublicProbe {
        @GetMapping("/public/probe") String publicProbe() { return "ok"; }
        @GetMapping("/private/probe") String privateProbe() { return "ok"; }
    }

    @Test
    void documentationRoutesAreClosedForAnonymousAndAuthenticatedRequests() throws Exception {
        for (String path : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs"}) {
            mvc.perform(get(path)).andExpect(status().is4xxClientError());
            mvc.perform(get(path).with(user("test").roles("ADMIN"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void privateEndpointsRequireAuthenticationWhilePublicProbeRemainsAvailable() throws Exception {
        mvc.perform(get("/public/probe")).andExpect(status().isOk());
        mvc.perform(get("/private/probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/private/probe").with(user("test"))).andExpect(status().isOk());
    }

    @Test
    void corsAcceptsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/private/probe")
                .header("Origin", "https://other.example.test")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/private/probe")
                .header("Origin", "https://front.example.test")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isOk());
    }

            @Test
            void applicationDataActionsAreDeniedToAnonymousAndEveryNonAdminRole() throws Exception {
            mvc.perform(get("/admin/application-data/export")).andExpect(status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/import")
                .contentType("application/zip").content(new byte[]{1})).andExpect(status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                .contentType("application/json").content("{\"confirmation\":\"REINITIALISER\"}"))
                .andExpect(status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset/request-code"))
                .andExpect(status().isUnauthorized());
            for (String role : new String[]{"INTERNAL", "BUSINESS_MANAGER", "FINANCE_MANAGER"}) {
                mvc.perform(get("/admin/application-data/export").with(user("test").roles(role)))
                    .andExpect(status().isForbidden());
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/import")
                    .with(user("test").roles(role)).contentType("application/zip").content(new byte[]{1}))
                    .andExpect(status().isForbidden());
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                    .with(user("test").roles(role)).contentType("application/json")
                    .content("{\"confirmation\":\"REINITIALISER\"}"))
                    .andExpect(status().isForbidden());
                    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset/request-code")
                        .with(user("test").roles(role))).andExpect(status().isForbidden());
            }
                    org.mockito.Mockito.verifyNoInteractions(backups, resetConfirmations);
            }

            @Test
            void adminCanDownloadAndImportButResetRequiresExplicitConfirmation() throws Exception {
            org.mockito.Mockito.when(backups.exportZip()).thenReturn(new byte[]{80, 75});
            mvc.perform(get("/admin/application-data/export").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                    .contentType("application/zip"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/import")
                .with(user("admin").roles("ADMIN")).contentType("application/zip").content(new byte[]{80, 75}))
                .andExpect(status().isNoContent());
            org.mockito.Mockito.verify(backups).importZip(new byte[]{80, 75});
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                .with(user("admin").roles("ADMIN")).contentType("application/json")
                .content("{\"confirmation\":\"non\"}"))
                .andExpect(status().isBadRequest());
            org.mockito.Mockito.verify(backups, org.mockito.Mockito.never()).reset(org.mockito.ArgumentMatchers.any());
            }

            private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(java.util.UUID userId) {
            var principal = new com.esn.inodoc.security.UserPrincipal(userId, "admin@example.test", "", true,
                java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
            }

            @Test
            void resetCannotRunWithJustTheOldConfirmationOrAnInvalidCode() throws Exception {
            java.util.UUID adminId = java.util.UUID.randomUUID();
            java.util.UUID challengeId = java.util.UUID.randomUUID();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                .with(adminPrincipal(adminId)).contentType("application/json")
                .content("{\"confirmation\":\"REINITIALISER\"}"))
                .andExpect(status().isBadRequest());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                .with(adminPrincipal(adminId)).contentType("application/json")
                .content("{\"confirmation\":\"REINITIALISER\",\"challengeId\":\"" + challengeId + "\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
            org.mockito.Mockito.verify(resetConfirmations).consume(adminId, null, null);
            org.mockito.Mockito.verify(resetConfirmations).consume(adminId, challengeId, "000000");
            org.mockito.Mockito.verifyNoInteractions(backups);
            }

            @Test
            void resetConsumesTheAdministratorsEmailCodeBeforeDeletingData() throws Exception {
            java.util.UUID adminId = java.util.UUID.randomUUID();
            java.util.UUID challengeId = java.util.UUID.randomUUID();
            org.mockito.Mockito.when(resetConfirmations.consume(adminId, challengeId, "123456")).thenReturn(true);
            org.mockito.Mockito.when(backups.reset(adminId)).thenReturn(new byte[]{80, 75});
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset")
                .with(adminPrincipal(adminId)).contentType("application/json")
                .content("{\"confirmation\":\"REINITIALISER\",\"challengeId\":\"" + challengeId + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/zip"));
            var order = org.mockito.Mockito.inOrder(resetConfirmations, backups);
            order.verify(resetConfirmations).consume(adminId, challengeId, "123456");
            order.verify(backups).reset(adminId);
            }

            @Test
            void sendingCodeUsesTheAuthenticatedAdminAndDoesNotExposeTheCodeInTheResponse() throws Exception {
            java.util.UUID adminId = java.util.UUID.randomUUID();
            java.util.UUID challengeId = java.util.UUID.randomUUID();
            org.mockito.Mockito.when(resetConfirmations.requestCode(adminId)).thenReturn(
                new com.esn.inodoc.service.ApplicationResetConfirmationService.Delivery(
                    challengeId, java.time.Instant.parse("2026-10-18T12:10:00Z"), "admin@example.test"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/application-data/reset/request-code")
                .with(adminPrincipal(adminId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").doesNotExist());
            org.mockito.Mockito.verify(resetConfirmations).requestCode(adminId);
            org.mockito.Mockito.verifyNoInteractions(backups);
            }
}