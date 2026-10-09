package com.esn.inodoc.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RequestLoggingFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsRequestActorStatusDurationAndNeverLogsQueryParameters() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);

        var authorities = Set.of(new SimpleGrantedAuthority("ROLE_BUSINESS_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("manager", null, authorities));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profiles");
        request.setQueryString("access_token=must-not-be-logged");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            new RequestLoggingFilter().doFilter(request, response,
                    (servletRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(204));

            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("method=GET"));
            assertTrue(message.contains("url=/api/profiles"));
            assertTrue(message.contains("userType=BUSINESS_MANAGER"));
            assertFalse(message.contains("userId="));
            assertTrue(message.contains("status=204"));
            assertTrue(message.matches(".*durationMs=\\d+$"));
            assertFalse(message.contains("must-not-be-logged"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void masksPublicShareTokensEmbeddedInThePath() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/share/private-token");
        request.setQueryString("password=private-password");
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "anonymous-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        try {
            new RequestLoggingFilter().doFilter(request, new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(200));

            String message = appender.list.get(0).getFormattedMessage();
            assertTrue(message.contains("url=/api/public/share/{token}"));
            assertFalse(message.contains("private-token"));
            assertFalse(message.contains("private-password"));
            assertTrue(message.contains("userType=ANONYMOUS"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}