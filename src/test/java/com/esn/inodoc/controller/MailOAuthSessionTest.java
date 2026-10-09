package com.esn.inodoc.controller;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.service.MailAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MailOAuthSessionTest {
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
        verify(service).completeAuthorization("code", "signed-state");

        controller.callback("code", "signed-state", null, session);
        verify(service, times(1)).completeAuthorization(anyString(), anyString());
    }
}
