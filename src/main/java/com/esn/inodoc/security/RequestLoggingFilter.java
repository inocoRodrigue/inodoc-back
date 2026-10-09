package com.esn.inodoc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            int status = response.getStatus();
            if (failed && status < HttpServletResponse.SC_BAD_REQUEST) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            RequestActor actor = currentActor();
            String url = sanitizedUrl(request.getRequestURI()).replaceAll("[\\r\\n\\t]", "_");
            String message = "http_request method={} url={} userType={} status={} durationMs={}";
            if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                log.error(message, request.getMethod(), url, actor.type(), status, durationMs);
            } else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
                log.warn(message, request.getMethod(), url, actor.type(), status, durationMs);
            } else {
                log.info(message, request.getMethod(), url, actor.type(), status, durationMs);
            }
        }
    }

    private String sanitizedUrl(String requestUri) {
        String tokenRoute = "/public/share/";
        int tokenStart = requestUri.indexOf(tokenRoute);
        if (tokenStart < 0) {
            return requestUri;
        }
        tokenStart += tokenRoute.length();
        int tokenEnd = requestUri.indexOf('/', tokenStart);
        if (tokenEnd < 0) {
            tokenEnd = requestUri.length();
        }
        return requestUri.substring(0, tokenStart) + "{token}" + requestUri.substring(tokenEnd);
    }

    private RequestActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return new RequestActor("ANONYMOUS");
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        String type = roles.contains("ROLE_ADMIN") ? "ADMIN"
                : roles.contains("ROLE_BUSINESS_MANAGER") ? "BUSINESS_MANAGER"
                : roles.contains("ROLE_INTERNAL") ? "INTERNAL"
                : roles.contains("ROLE_EXTERNAL") ? "EXTERNAL"
                : "AUTHENTICATED";
        return new RequestActor(type);
    }

    private record RequestActor(String type) {
    }
}