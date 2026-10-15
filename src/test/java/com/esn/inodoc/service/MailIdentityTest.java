package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailIdentityTest {
    private final MailAccountRepository accounts = mock(MailAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleOAuthService google = mock(GoogleOAuthService.class);
    private final MailCryptoService crypto = mock(MailCryptoService.class);
    private final MailAccountService service = new MailAccountService(accounts, crypto, google, users);
    private User user;

    @BeforeEach
    void signIn() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@example.test");
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cannotReadSendOrDeleteAnotherUsersConnection() {
        var account = new MailAccount();
        account.setId(UUID.randomUUID());
        account.setCreatedBy(UUID.randomUUID());
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        assertThrows(AccessDeniedException.class, () -> service.requireActive(account.getId()));
        assertThrows(AccessDeniedException.class, () -> service.delete(account.getId()));
        assertThrows(AccessDeniedException.class, () -> new EmailService(google, crypto, users)
                .send(account, "to@example.test", "Subject", "Body", null, null));
        verifyNoInteractions(google, crypto);
    }

    @Test
    void callbackCannotTakeOverAnotherUsersGoogleAccount() {
        var account = new MailAccount();
        account.setId(UUID.randomUUID());
        account.setCreatedBy(UUID.randomUUID());
        when(google.verifyState("state")).thenReturn(user.getId());
        when(google.exchangeCode("code")).thenReturn(
                new GoogleOAuthService.ConnectedGoogleAccount("google@example.test", "refresh"));
        when(accounts.findByEmailIgnoreCase("google@example.test")).thenReturn(Optional.of(account));
        assertThrows(ConflictException.class, () -> service.completeAuthorization("code", "state"));
        verify(accounts, never()).save(any());
        verifyNoInteractions(crypto);
    }

    @Test
    void sendRejectsMismatchBetweenTokenAndStoredSender() {
        var account = new MailAccount();
        account.setCreatedBy(user.getId());
        account.setEmail("google@example.test");
        account.setRefreshTokenEncrypted("encrypted");
        when(crypto.decrypt("encrypted")).thenReturn("refresh");
        when(google.refreshAccessToken("refresh")).thenReturn("access");
        when(google.fetchEmail("access")).thenReturn("someone-else@example.test");
        assertThrows(AccessDeniedException.class, () -> new EmailService(google, crypto, users)
                .send(account, "to@example.test", "Subject", "Body", null, null));
    }

    @Test
    void listingIsAlwaysScopedToAuthenticatedUser() {
        service.list();
        verify(accounts).findAllByCreatedByAndActiveTrueOrderByCreatedAtAsc(user.getId());
        verify(accounts, never()).findAllByOrderByCreatedAtAsc();
    }

    @Test
    void ssoStoresTheGmailRefreshTokenEncryptedForItsUser() {
        when(accounts.findByCreatedByAndActiveTrue(user.getId())).thenReturn(Optional.empty());
        when(accounts.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.empty());
        when(crypto.encrypt("google-refresh")).thenReturn("encrypted-refresh");

        service.connectFromSso(user.getId(), "Owner@Example.Test", "google-refresh");

        var captor = org.mockito.ArgumentCaptor.forClass(MailAccount.class);
        verify(accounts).save(captor.capture());
        assertEquals("owner@example.test", captor.getValue().getEmail());
        assertEquals(user.getId(), captor.getValue().getCreatedBy());
        assertEquals("encrypted-refresh", captor.getValue().getRefreshTokenEncrypted());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void ssoDoesNotReplaceAnExistingDifferentMailConnection() {
        var existing = new MailAccount();
        existing.setEmail("another@example.test");
        existing.setCreatedBy(user.getId());
        when(accounts.findByCreatedByAndActiveTrue(user.getId())).thenReturn(Optional.of(existing));

        service.connectFromSso(user.getId(), "owner@example.test", "google-refresh");

        verify(accounts, never()).save(any());
        verifyNoInteractions(crypto);
    }

    @Test
    void sendsSavedSignatureAndVerifiedFromThroughMockGmail() throws Exception {
        var role = new com.esn.inodoc.domain.Role();
        role.setName(com.esn.inodoc.domain.RoleName.BUSINESS_MANAGER);
        user.setRoles(java.util.Set.of(role));
        assertEmailBody("Body\n\nSaved signature");
    }

    @Test
    void internalAccountDoesNotAppendLegacySignature() throws Exception {
        var role = new com.esn.inodoc.domain.Role();
        role.setName(com.esn.inodoc.domain.RoleName.INTERNAL);
        user.setRoles(java.util.Set.of(role));
        assertEmailBody("Body");
    }

    private void assertEmailBody(String expectedBody) throws Exception {
        user.setSignature("Saved signature");
        var account = new MailAccount();
        account.setCreatedBy(user.getId());
        account.setEmail("google@example.test");
        account.setRefreshTokenEncrypted("encrypted");
        when(crypto.decrypt("encrypted")).thenReturn("refresh");
        when(google.refreshAccessToken("refresh")).thenReturn("access");
        when(google.fetchEmail("access")).thenReturn("google@example.test");
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(request -> {
                    String json = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    String raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("raw").asText();
                    byte[] bytes = java.util.Base64.getUrlDecoder().decode(raw);
                    try {
                        var message = new jakarta.mail.internet.MimeMessage(
                                jakarta.mail.Session.getInstance(new java.util.Properties()),
                                new java.io.ByteArrayInputStream(bytes));
                        assertEquals("google@example.test",
                                ((jakarta.mail.internet.InternetAddress) message.getFrom()[0]).getAddress());
                        assertEquals(expectedBody,
                                message.getContent().toString().replace("\r\n", "\n").stripTrailing());
                    } catch (jakarta.mail.MessagingException ex) {
                        throw new AssertionError(ex);
                    }
                })
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
        new EmailService(google, crypto, users, builder.build())
                .send(account, "to@example.test", "Subject", "Body", null, null);
        server.verify();
    }
}
