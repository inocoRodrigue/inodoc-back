package com.esn.inodoc.security;

import com.esn.inodoc.service.CraFeatureService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CraFeatureFilter extends OncePerRequestFilter {
    private final CraFeatureService featureService;

    public CraFeatureFilter(CraFeatureService featureService) {
        this.featureService = featureService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals("/cra") && !path.startsWith("/cra/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!featureService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}