package com.esn.inodoc.security;

import com.esn.inodoc.service.CraFeatureService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraFeatureFilterTest {
    private final CraFeatureService service = mock(CraFeatureService.class);
    private final CraFeatureFilter filter = new CraFeatureFilter(service);

    @Test
    void hidesCraEndpointsWhenFeatureIsDisabled() throws Exception {
        when(service.isEnabled()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cra/entries");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(404, response.getStatus());
        verify(service).isEnabled();
        assertNull(chain.getRequest());
    }

    @Test
    void letsCraEndpointsThroughWhenFeatureIsEnabled() throws Exception {
        when(service.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cra/entries");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
    }

    @Test
    void doesNotGateUnrelatedEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(service, never()).isEnabled();
        assertSame(request, chain.getRequest());
    }
}