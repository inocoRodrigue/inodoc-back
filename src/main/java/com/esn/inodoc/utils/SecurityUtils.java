package com.esn.inodoc.utils;

import com.esn.inodoc.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Simplified access to the current authenticated principal.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static UserPrincipal requirePrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new IllegalStateException("Aucun utilisateur authentifié"));
    }
}
