package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.GlobalExceptionHandler;
import com.esn.inodoc.controller.error.GoneException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.SharedLink;
import com.esn.inodoc.repo.CompetenceDossierRepository;
import com.esn.inodoc.repo.SharedLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareLinkAccessTest {
    private SharedLinkRepository links;
    private ShareService service;

    @BeforeEach
    void setUp() {
        links = mock(SharedLinkRepository.class);
        service = new ShareService(links, mock(CompetenceDossierRepository.class),
                mock(PasswordEncoder.class), new AppProperties(),
                mock(CompanySettingsService.class), mock(MailAccountService.class),
                mock(EmailService.class), mock(PdfService.class), mock(S3ImageStorage.class));
    }

    @Test
    void unknownTokenReturnsNotFound() {
        when(links.findByTokenWithDossier("unknown")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.access("unknown", null));
        verify(links, never()).save(any());
    }

    @Test
    void expiredTokenIsGoneWithoutIncrementingOpenCount() {
        SharedLink link = new SharedLink();
        link.setExpiresAt(Instant.now().minusSeconds(1));
        when(links.findByTokenWithDossier("expired")).thenReturn(Optional.of(link));
        assertThrows(GoneException.class, () -> service.access("expired", null));
        assertEquals(0, link.getOpenCount());
        verify(links, never()).save(any());
    }

    @Test
    void revokedAndExhaustedTokensAreGone() {
        SharedLink link = new SharedLink();
        link.setRevoked(true);
        when(links.findByTokenWithDossier("token")).thenReturn(Optional.of(link));
        assertThrows(GoneException.class, () -> service.access("token", null));
        link.setRevoked(false);
        link.setMaxOpens(1);
        link.setOpenCount(1);
        assertThrows(GoneException.class, () -> service.access("token", null));
        verify(links, never()).save(any());
    }

    @Test
    void passwordChallengeStillReturnsBadRequestRatherThanGone() {
        SharedLink link = new SharedLink();
        link.setPasswordHash("hash");
        when(links.findByTokenWithDossier("protected")).thenReturn(Optional.of(link));
        assertThrows(BadRequestException.class, () -> service.access("protected", null));
    }

    @Test
    void goneExceptionMapsToHttp410() {
        var response = new GlobalExceptionHandler().handleGone(new GoneException("Lien expire"));
        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals(410, response.getBody().get("status"));
    }
}
