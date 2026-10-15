package com.esn.inodoc.controller;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.service.MailAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MailOAuthSessionTest {
    @Test
    void callbackPreservesSafeGoogleExchangeErrorForTheUser() {
        var service = mock(MailAccountService.class);
        var controller = new MailAccountController(service, new AppProperties());
        var session = new MockHttpSession();
        when(service.beginAuthorization()).thenReturn("https://accounts.google.com/auth?state=signed-state");
        controller.authorize(session);
        doThrow(new BadRequestException("Google n'a pas renvoyé de refresh token. Révoquez son accès puis réessayez."))
                .when(service).completeAuthorization("code", "signed-state");

        var response = controller.callback("code", "signed-state", null, session);
        String encodedError = org.springframework.web.util.UriComponentsBuilder.fromUri(
                response.getHeaders().getLocation()).build().getQueryParams().getFirst("mailError");

        assertEquals("Google n'a pas renvoyé de refresh token. Révoquez son accès puis réessayez.",
            URLDecoder.decode(encodedError, StandardCharsets.UTF_8));
    }

    @Test
    void callbackMustUseTheInitiatingSessionAndStateOnlyOnce() {
        var service = mock(MailAccountService.class);
        var properties = new AppProperties();
        var controller = new MailAccountController(service, properties);
        var session = new MockHttpSession();
        when(service.beginAuthorization()).thenReturn("https://accounts.google.com/auth?state=signed-state");

        controller.authorize(session);
        var rejected = controller.callback("code", "signed-state", null, new MockHttpSession());
        assertTrue(rejected.getHeaders().getLocation().toString().contains("mailError="));
        verify(service, never()).completeAuthorization(anyString(), anyString());

        var accepted = controller.callback("code", "signed-state", null, session);
        assertTrue(accepted.getHeaders().getLocation().toString().contains("mail=connected"));
        assertTrue(accepted.getHeaders().getLocation().toString().startsWith("http://localhost:4200/account"));
        verify(service).completeAuthorization("code", "signed-state");

        controller.callback("code", "signed-state", null, session);
        verify(service, times(1)).completeAuthorization(anyString(), anyString());
    }
}
