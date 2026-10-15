package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.dto.auth.TokenResponse;
import com.esn.inodoc.dto.user.UserDto;
import com.esn.inodoc.service.GoogleOAuthService.GoogleProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GoogleLoginServiceTest {
    @Test
    void ssoRequestsOfflineGmailPermissionAndStoresTheReturnedGrant() {
        var google = mock(GoogleOAuthService.class);
        var auth = mock(AuthService.class);
        var mailAccounts = mock(MailAccountService.class);
        var properties = new AppProperties();
        properties.getSso().getGoogle().setLoginRedirectUri("https://backend.example.test/auth/callback");
        properties.getMail().getGoogle().setClientId("client-id");
        properties.getMail().getGoogle().setClientSecret("client-secret");
        UUID userId = UUID.randomUUID();
        var user = new UserDto(userId, "person@example.test", "First", "Last", true,
                Set.of("INTERNAL"), null, null, null);
        var tokens = new TokenResponse("access", "refresh", "Bearer", 15, user);
        when(google.exchangeLoginCode("https://backend.example.test/auth/callback", "code"))
                .thenReturn(new GoogleProfile("person@example.test", "First", "Last", "google-refresh"));
        when(auth.ssoLogin("person@example.test", "First", "Last")).thenReturn(tokens);
        var service = new GoogleLoginService(google, auth, properties, mailAccounts);

        String authorizationUrl = new GoogleOAuthService(properties).buildLoginUrl(
                "https://backend.example.test/auth/callback", "state");
        var query = org.springframework.web.util.UriComponentsBuilder.fromUriString(authorizationUrl)
                .build(true).getQueryParams();
        assertTrue(query.getFirst("scope").contains("https://www.googleapis.com/auth/gmail.send"));
        assertTrue("offline".equals(query.getFirst("access_type")));
        assertTrue(query.getFirst("prompt").contains("consent"));

        assertSame(tokens, service.completeLogin("code", "state"));
        verify(mailAccounts).connectFromSso(userId, "person@example.test", "google-refresh");
    }
}