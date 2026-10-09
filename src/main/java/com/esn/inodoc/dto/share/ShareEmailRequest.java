package com.esn.inodoc.dto.share;

import com.esn.inodoc.domain.ShareEmailContent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Option to email a share link, on behalf of a connected Gmail account.
 * The subject, body and signature are written by the user; if they are
 * empty, default values are applied server-side. The body may
 * contain the {@code {{lien}}} marker which will be replaced by the secure URL.
 */
public record ShareEmailRequest(
        @NotBlank @Email String recipient,
        @NotNull UUID accountId,
        @NotNull ShareEmailContent content,
        String subject,
        String body,
        String signature) {
}
