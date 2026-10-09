package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

/**
 * Sending emails via the Gmail API on behalf of a Google account connected in OAuth2.
 * A fresh access token is obtained from the stored refresh token, then the
 * MIME message is encoded in base64url and sent to the Gmail endpoint.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String GMAIL_SEND_ENDPOINT =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final GoogleOAuthService googleOAuthService;
    private final MailCryptoService cryptoService;
    private final UserRepository users;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(GoogleOAuthService googleOAuthService, MailCryptoService cryptoService,
                        UserRepository users) {
        this(googleOAuthService, cryptoService, users, RestClient.create());
    }

    EmailService(GoogleOAuthService googleOAuthService, MailCryptoService cryptoService,
                 UserRepository users, RestClient restClient) {
        this.googleOAuthService = googleOAuthService;
        this.cryptoService = cryptoService;
        this.users = users;
        this.restClient = restClient;
    }

    /**
     * Sends an email on behalf of the given Google account via the Gmail API.
     *
     * @param account        sender Google account
     * @param to             recipient
     * @param subject        subject
     * @param body           message body (text)
     * @param attachment     optional PDF attachment (may be null)
     * @param attachmentName attachment file name
     */
    public void send(MailAccount account, String to, String subject, String body,
                     byte[] attachment, String attachmentName) {
        var userId = SecurityUtils.requirePrincipal().getUserId();
        if (!account.isActive() || !userId.equals(account.getCreatedBy())) {
            throw new AccessDeniedException("Compte Google non autorisé");
        }
        var user = users.findById(userId).filter(u -> u.isEnabled())
                .orElseThrow(() -> new AccessDeniedException("Compte utilisateur indisponible"));
        String refreshToken = cryptoService.decrypt(account.getRefreshTokenEncrypted());
        String accessToken = googleOAuthService.refreshAccessToken(refreshToken);
        if (!account.getEmail().equalsIgnoreCase(googleOAuthService.fetchEmail(accessToken))) {
            throw new AccessDeniedException("L'identité Google ne correspond pas à l'expéditeur");
        }
        String signature = user.canUseMailSignature() ? user.getSignature() : null;
        String fullBody = signature == null || signature.isBlank() ? body : body + "\n\n" + signature;
        String rawMessage = buildRawMessage(account, to, subject, fullBody, attachment, attachmentName);
        try {
            restClient.post()
                    .uri(GMAIL_SEND_ENDPOINT)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", rawMessage))
                    .retrieve()
                    .toBodilessEntity();
        } catch (org.springframework.web.client.RestClientException e) {
            // Never log the tokens.
            log.warn("Échec de l'envoi Gmail");
            throw new BadRequestException(
                    "Échec de l'envoi de l'email : vérifiez le compte Google connecté");
        }
    }

    private String buildRawMessage(MailAccount account, String to, String subject, String body,
                                   byte[] attachment, String attachmentName) {
        try {
            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            boolean multipart = attachment != null && attachment.length > 0;
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());
            helper.setFrom(account.getEmail(), displayName(account));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (multipart) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return Base64.getUrlEncoder().encodeToString(out.toByteArray());
        } catch (jakarta.mail.MessagingException | java.io.IOException e) {
            throw new BadRequestException("Impossible de construire l'email");
        }
    }

    private String displayName(MailAccount account) {
        return account.getDisplayName() != null && !account.getDisplayName().isBlank()
                ? account.getDisplayName() : "inoDoc";
    }
}
