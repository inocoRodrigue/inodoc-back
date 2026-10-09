package com.esn.inodoc.dto.mail;

import java.util.UUID;

/**
 * Public view of a shared Gmail account. Never contains the password.
 */
public record MailAccountDto(
        UUID id,
        String email,
        String displayName,
        boolean active) {
}
