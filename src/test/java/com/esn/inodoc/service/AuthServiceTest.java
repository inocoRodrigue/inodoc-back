package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.config.JwtProperties;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.RefreshToken;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.auth.RefreshRequest;
import com.esn.inodoc.repo.RefreshTokenRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test
    void repeatedLoginFailuresAreRateLimitedAcrossEmailCaseVariants() {
    var users = mock(UserRepository.class);
    var service = new AuthService(users, mock(RoleRepository.class), mock(ProfileService.class),
        mock(RefreshTokenRepository.class), mock(JwtService.class), new JwtProperties(),
        mock(PasswordEncoder.class), new AppProperties());
    for (int attempt = 0; attempt < 10; attempt++) {
        String email = attempt % 2 == 0 ? "Alice@Example.test" : " alice@example.test ";
        assertThrows(BadRequestException.class, () -> service.login(
            new com.esn.inodoc.dto.auth.LoginRequest(email, "incorrect")));
    }
    var limited = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.login(
        new com.esn.inodoc.dto.auth.LoginRequest("alice@example.test", "incorrect")));
    org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
        limited.getStatusCode());
    var response = new com.esn.inodoc.controller.error.GlobalExceptionHandler().handleStatus(limited);
    org.junit.jupiter.api.Assertions.assertEquals("900", response.getHeaders().getFirst("Retry-After"));
    org.junit.jupiter.api.Assertions.assertEquals(limited.getReason(), response.getBody().get("message"));
    verify(users, times(10)).findByEmailIgnoreCase("alice@example.test");
    assertThrows(BadRequestException.class, () -> service.login(
        new com.esn.inodoc.dto.auth.LoginRequest("another@example.test", "incorrect")));
    }

    @Test
    void disabledAccountCannotRefreshEvenWithAnUnexpiredToken() {
        var tokens = mock(RefreshTokenRepository.class);
        var jwt = mock(JwtService.class);
        var service = new AuthService(mock(UserRepository.class), mock(RoleRepository.class),
                mock(ProfileService.class), tokens, jwt, new JwtProperties(),
                mock(PasswordEncoder.class), new AppProperties());
        var user = new User();
        user.setEnabled(false);
        var token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        assertThrows(BadRequestException.class, () -> service.refresh(new RefreshRequest("valid-token")));
        verify(tokens, never()).save(any());
        verifyNoInteractions(jwt);
    }
}