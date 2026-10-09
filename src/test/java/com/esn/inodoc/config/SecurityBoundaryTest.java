package com.esn.inodoc.config;

import com.esn.inodoc.security.JwtAuthenticationFilter;
import com.esn.inodoc.security.JwtService;
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

@WebMvcTest(controllers = SecurityBoundaryTest.PublicProbe.class,
    properties = "inodoc.cors.allowed-origins=https://front.example.test")
@EnableConfigurationProperties(AppProperties.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    SecurityBoundaryTest.PublicProbe.class})
class SecurityBoundaryTest {
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository users;
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
}