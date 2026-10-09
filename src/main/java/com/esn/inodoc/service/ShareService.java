package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ShareEmailContent;
import com.esn.inodoc.domain.SharedLink;
import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.dossier.DossierMapper;
import com.esn.inodoc.dto.share.CreateShareRequest;
import com.esn.inodoc.dto.share.ShareEmailRequest;
import com.esn.inodoc.dto.share.SharedDossierView;
import com.esn.inodoc.dto.share.SharedEditRequest;
import com.esn.inodoc.dto.share.SharedLinkDto;
import com.esn.inodoc.repo.CompetenceDossierRepository;
import com.esn.inodoc.repo.SharedLinkRepository;
import com.esn.inodoc.utils.SecurityUtils;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.GoneException;
import com.esn.inodoc.controller.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Management of secure share links to a dossier version.
 */
@Service
public class ShareService {

    private final SharedLinkRepository sharedLinkRepository;
    private final CompetenceDossierRepository dossierRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final CompanySettingsService companySettingsService;
    private final MailAccountService mailAccountService;
    private final EmailService emailService;
    private final PdfService pdfService;
    private final S3ImageStorage storage;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(SharedLinkRepository sharedLinkRepository,
                        CompetenceDossierRepository dossierRepository,
                        PasswordEncoder passwordEncoder,
                        AppProperties appProperties,
                        CompanySettingsService companySettingsService,
                        MailAccountService mailAccountService,
                        EmailService emailService,
                        PdfService pdfService,
                        S3ImageStorage storage) {
        this.sharedLinkRepository = sharedLinkRepository;
        this.dossierRepository = dossierRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.companySettingsService = companySettingsService;
        this.mailAccountService = mailAccountService;
        this.emailService = emailService;
        this.pdfService = pdfService;
        this.storage = storage;
    }

    @Transactional
    public SharedLinkDto create(CreateShareRequest request) {
        CompetenceDossier dossier = dossierRepository.findById(request.dossierId())
                .orElseThrow(() -> new NotFoundException("Version de dossier introuvable"));

        if (dossier.isMaster() && request.type() == com.esn.inodoc.domain.ShareType.COLLABORATOR_EDIT) {
            throw new BadRequestException("Le dossier master ne peut pas être partagé en mode édition");
        }

        SharedLink link = new SharedLink();
        link.setDossier(dossier);
        link.setType(request.type());
        link.setToken(generateToken());
        if (request.password() != null && !request.password().isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            link.setExpiresAt(Instant.now().plus(request.expiresInDays(), ChronoUnit.DAYS));
        }
        link.setMaxOpens(request.maxOpens());
        SecurityUtils.getCurrentPrincipal().ifPresent(p -> link.setCreatedBy(p.getUserId()));

        SharedLink saved = sharedLinkRepository.save(link);

        if (request.email() != null) {
            sendShareEmail(saved, dossier, request.email());
        }
        return toDto(saved);
    }

    /**
     * Sends the share link (and/or the dossier PDF) by email on behalf of the
     * user's own Gmail account. The saved personal signature is appended by
     * EmailService. The body may contain
     * the {@code {{lien}}} marker which will be replaced by the URL; otherwise
     * the link is appended at the end of the message. A send failure rolls back
     * the link creation.
     */
    private void sendShareEmail(SharedLink link, CompetenceDossier dossier, ShareEmailRequest email) {
        MailAccount account = mailAccountService.requireActive(email.accountId());
        ShareEmailContent content = email.content();
        String personName = profileName(dossier);

        String subject = hasText(email.subject())
                ? email.subject().trim()
                : "Dossier de compétences - " + personName;

        String body = hasText(email.body())
                ? email.body()
                : defaultEmailBody(personName);

        byte[] attachment = null;
        String attachmentName = null;
        if (content == ShareEmailContent.PDF || content == ShareEmailContent.BOTH) {
            attachment = pdfService.generateDossierPdf(dossier);
            attachmentName = "Dossier_"
                    + personName.replaceAll("[^A-Za-z0-9]", "_") + ".pdf";
        }

        String url = null;
        if (content == ShareEmailContent.LINK || content == ShareEmailContent.BOTH) {
            url = appProperties.getShare().getBaseUrl() + "/" + link.getToken();
        }

        // Link insertion: replaces the {{lien}} marker if present,
        // otherwise appends a dedicated line at the end of the body.
        if (body.contains("{{lien}}")) {
            body = body.replace("{{lien}}", url != null ? url : "");
        } else if (url != null) {
            body = body + "\n\nLien de consultation sécurisé : " + url;
        }

        emailService.send(account, email.recipient(), subject, body, attachment, attachmentName);
    }

    /** Default text proposed for the email body (editable by the user). */
    private String defaultEmailBody(String personName) {
        return "Bonjour,\n\n"
                + "Veuillez trouver le dossier de compétences de " + personName + ".";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Transactional(readOnly = true)
    public Page<SharedLinkDto> list(Pageable pageable) {
        return sharedLinkRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toDto);
    }

    @Transactional
    public void revoke(UUID id) {
        SharedLink link = sharedLinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lien de partage introuvable"));
        link.setRevoked(true);
        sharedLinkRepository.save(link);
    }

    @Transactional
    public SharedDossierView access(String token, String password) {
        SharedLink link = requireUsableLink(token, password);
        link.setOpenCount(link.getOpenCount() + 1);
        sharedLinkRepository.save(link);

        CompetenceDossier dossier = link.getDossier();
        return new SharedDossierView(
                DossierMapper.toDto(dossier, storage::signedUrl),
                profileInitials(dossier),
                companySettingsService.get(),
                link.isEditable());
    }

    @Transactional
    public DossierDto editViaShare(String token, SharedEditRequest request) {
        SharedLink link = requireUsableLink(token, request.password());
        if (!link.isEditable()) {
            throw new BadRequestException("Ce lien ne permet pas la modification");
        }
        CompetenceDossier dossier = link.getDossier();
        DossierMapper.apply(dossier, request.dossier(), storage::storedUrl);
        dossier = dossierRepository.save(dossier);
        return DossierMapper.toDto(dossier, storage::signedUrl);
    }

    private SharedLink requireUsableLink(String token, String password) {
        // Use eager loading query to prevent N+1 issue when accessing dossier
        SharedLink link = sharedLinkRepository.findByTokenWithDossier(token)
                .orElseThrow(() -> new NotFoundException("Lien de partage introuvable"));
        if (link.isRevoked()) {
            throw new GoneException("Lien revoque");
        }
        if (link.isExpired()) {
            throw new GoneException("Lien expiré");
        }
        if (link.isOpenLimitReached()) {
            throw new GoneException("Nombre maximum d'ouvertures atteint");
        }
        if (link.isPasswordProtected()) {
            if (password == null || !passwordEncoder.matches(password, link.getPasswordHash())) {
                throw new BadRequestException("Mot de passe invalide");
            }
        }
        return link;
    }

    private SharedLinkDto toDto(SharedLink link) {
        String url = appProperties.getShare().getBaseUrl() + "/" + link.getToken();
        CompetenceDossier dossier = link.getDossier();
        return new SharedLinkDto(
                link.getId(),
                link.getToken(),
                url,
                link.getType(),
                dossier.getId(),
                profileName(dossier),
                dossier.getLabel(),
                link.getExpiresAt(),
                link.getMaxOpens(),
                link.getOpenCount(),
                link.isPasswordProtected(),
                link.isRevoked(),
                link.isActive());
    }

    private String profileName(CompetenceDossier dossier) {
        Profile p = dossier.getProfile();
        return p.getFirstName() + " " + p.getLastName();
    }

    /**
     * Profile initials for client-side anonymization (e.g. "B. M.").
     */
    private String profileInitials(CompetenceDossier dossier) {
        Profile p = dossier.getProfile();
        String first = initial(p.getFirstName());
        String last = initial(p.getLastName());
        return (first + " " + last).trim();
    }

    private String initial(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.trim().charAt(0)) + ".";
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
