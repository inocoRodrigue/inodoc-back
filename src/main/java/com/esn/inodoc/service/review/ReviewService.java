package com.esn.inodoc.service.review;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.review.CampaignStatus;
import com.esn.inodoc.domain.review.ReferenceDocument;
import com.esn.inodoc.domain.review.ReviewCampaign;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.domain.review.ReviewSubmission;
import com.esn.inodoc.domain.review.SubmissionStatus;
import com.esn.inodoc.dto.review.CampaignDto;
import com.esn.inodoc.dto.review.CampaignRequest;
import com.esn.inodoc.dto.review.ReferenceDocumentDto;
import com.esn.inodoc.dto.review.ReferenceDocumentRequest;
import com.esn.inodoc.dto.review.ReviewFormDto;
import com.esn.inodoc.dto.review.ReviewFormRequest;
import com.esn.inodoc.dto.review.SubmissionDto;
import com.esn.inodoc.dto.review.SubmissionRequest;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewCampaignRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.esn.inodoc.repo.review.ReviewSubmissionRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Review module: consultative reference documents, fillable form templates,
 * annual / semi-annual campaigns and one submission per (employee, campaign).
 * Management is restricted to administrators and business managers; internal
 * employees can view open campaigns and save their own submission.
 */
@Service
public class ReviewService {

    private static final int MAX_JSON_CHARS = 300_000;

    private final ReferenceDocumentRepository referenceRepository;
    private final ReviewFormRepository formRepository;
    private final ReviewCampaignRepository campaignRepository;
    private final ReviewSubmissionRepository submissionRepository;
    private final ReviewSubmissionSharingService submissionSharingService;
    private final UserRepository userRepository;
    private final ObjectMapper mapper;

    public ReviewService(ReferenceDocumentRepository referenceRepository,
                         ReviewFormRepository formRepository,
                         ReviewCampaignRepository campaignRepository,
                         ReviewSubmissionRepository submissionRepository,
                         ReviewSubmissionSharingService submissionSharingService,
                         UserRepository userRepository,
                         ObjectMapper mapper) {
        this.referenceRepository = referenceRepository;
        this.formRepository = formRepository;
        this.campaignRepository = campaignRepository;
        this.submissionRepository = submissionRepository;
        this.submissionSharingService = submissionSharingService;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- References

    @Transactional(readOnly = true)
    public List<ReferenceDocumentDto> listReferences() {
        return referenceRepository.findAllByOrderByCategoryAscTitleAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReferenceDocumentDto getReference(UUID id) {
        return toDto(requireReference(id));
    }

    @Transactional
    public ReferenceDocumentDto createReference(ReferenceDocumentRequest request) {
        requireManager();
        ReferenceDocument doc = new ReferenceDocument();
        applyReference(doc, request);
        doc.setCreatedBy(currentUserId());
        return toDto(referenceRepository.save(doc));
    }

    @Transactional
    public ReferenceDocumentDto updateReference(UUID id, ReferenceDocumentRequest request) {
        requireManager();
        ReferenceDocument doc = requireReference(id);
        applyReference(doc, request);
        if (doc.getCreatedBy() == null) {
            doc.setCreatedBy(currentUserId());
        }
        return toDto(referenceRepository.save(doc));
    }

    @Transactional
    public void deleteReference(UUID id) {
        requireManager();
        referenceRepository.delete(requireReference(id));
    }

    private void applyReference(ReferenceDocument doc, ReferenceDocumentRequest request) {
        doc.setTitle(requireTitle(request.title()));
        doc.setDescription(trimToNull(request.description()));
        doc.setCategory(trimToNull(request.category()));
        doc.setContent(writeJson(request.content()));
    }

    // --------------------------------------------------------------------- Forms

    @Transactional(readOnly = true)
    public List<ReviewFormDto> listForms() {
        requireManager();
        return formRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReviewFormDto getForm(UUID id) {
        return toDto(requireForm(id));
    }

    @Transactional
    public ReviewFormDto createForm(ReviewFormRequest request) {
        requireManager();
        ReviewForm form = new ReviewForm();
        applyForm(form, request);
        form.setCreatedBy(currentUserId());
        return toDto(formRepository.save(form));
    }

    @Transactional
    public ReviewFormDto updateForm(UUID id, ReviewFormRequest request) {
        requireManager();
        ReviewForm form = requireForm(id);
        applyForm(form, request);
        if (form.getCreatedBy() == null) {
            form.setCreatedBy(currentUserId());
        }
        return toDto(formRepository.save(form));
    }

    @Transactional
    public void deleteForm(UUID id) {
        requireManager();
        ReviewForm form = requireForm(id);
        if (campaignRepository.existsByFormId(id)) {
            throw new ConflictException("Ce modèle est utilisé par une campagne et ne peut pas être supprimé.");
        }
        formRepository.delete(form);
    }

    private void applyForm(ReviewForm form, ReviewFormRequest request) {
        form.setTitle(requireTitle(request.title()));
        form.setDescription(trimToNull(request.description()));
        form.setSchema(writeJson(request.schema()));
    }

    // ----------------------------------------------------------------- Campaigns

    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaigns() {
        List<ReviewCampaign> campaigns = isManager()
                ? campaignRepository.findAllWithForm()
                : campaignRepository.findByStatusWithForm(CampaignStatus.OPEN);
        return campaigns.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDto getCampaign(UUID id) {
        ReviewCampaign campaign = requireCampaign(id);
        if (!isManager() && campaign.getStatus() != CampaignStatus.OPEN) {
            throw new AccessDeniedException("Cette campagne n'est pas ouverte.");
        }
        return toDto(campaign);
    }

    @Transactional
    public CampaignDto createCampaign(CampaignRequest request) {
        requireManager();
        ReviewForm form = requireForm(request.formId());
        ReviewCampaign campaign = new ReviewCampaign();
        campaign.setTitle(requireTitle(request.title()));
        campaign.setType(request.type());
        campaign.setForm(form);
        campaign.setFormTitle(form.getTitle());
        campaign.setFormSchema(form.getSchema());
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setCreatedBy(currentUserId());
        return toDto(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDto updateCampaignStatus(UUID id, CampaignStatus status) {
        requireManager();
        ReviewCampaign campaign = requireCampaign(id);
        campaign.setStatus(status);
        return toDto(campaignRepository.save(campaign));
    }

    @Transactional
    public void deleteCampaign(UUID id) {
        requireManager();
        campaignRepository.delete(requireCampaign(id));
    }

    // --------------------------------------------------------------- Submissions

    /** The current employee's submission for a campaign (empty draft if none). */
    @Transactional(readOnly = true)
    public SubmissionDto getMySubmission(UUID campaignId) {
        ReviewCampaign campaign = requireCampaign(campaignId);
        UUID userId = currentInternalUserId();
        return submissionRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(this::toDto)
                .orElseGet(() -> new SubmissionDto(null, campaign.getId(), userId,
                        currentUserName(), mapper.createObjectNode(), SubmissionStatus.DRAFT, null, null));
    }

    @Transactional
    public SubmissionDto saveMySubmission(UUID campaignId, SubmissionRequest request) {
        ReviewCampaign campaign = requireCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.OPEN) {
            throw new BadRequestException("Cette campagne n'est pas ouverte aux réponses.");
        }
        UUID userId = currentInternalUserId();
        ReviewSubmission submission = submissionRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseGet(() -> {
                    ReviewSubmission created = new ReviewSubmission();
                    created.setCampaign(campaign);
                    created.setUserId(userId);
                    return created;
                });
        submission.setAnswers(writeJson(request.answers()));
        if (request.submit()) {
            submission.setStatus(SubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(Instant.now());
        } else {
            submission.setStatus(SubmissionStatus.DRAFT);
            submission.setSubmittedAt(null);
        }
        try {
            return toDto(submissionRepository.saveAndFlush(submission));
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Concurrent first save for the same (campaign, user).
            throw new ConflictException("Une réponse existe déjà pour cette campagne.");
        }
    }

    /** Lists submitted annual reviews explicitly shared with the current business manager. */
    @Transactional(readOnly = true)
    public List<SubmissionDto> listCampaignSubmissions(UUID campaignId) {
        requireManager();
        requireCampaign(campaignId);
        return submissionSharingService.listSharedAnnualSubmissions(campaignId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDto getSubmission(UUID id) {
        ReviewSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Réponse introuvable."));
        UUID requesterId = currentUserId();
        boolean owner = submission.getUserId().equals(requesterId);
        boolean sharedWithRequester = isManager()
            && submissionSharingService.isSharedWithBusinessManager(id, requesterId);
        if (!owner && !sharedWithRequester) {
            throw new AccessDeniedException("Cette réponse ne vous a pas été partagée.");
        }
        return toDto(submission);
    }

    // ------------------------------------------------------------------ Mappers

    private ReferenceDocumentDto toDto(ReferenceDocument doc) {
        return new ReferenceDocumentDto(doc.getId(), doc.getTitle(), doc.getDescription(),
                doc.getCategory(), readJson(doc.getContent()), doc.getCreatedAt(), doc.getUpdatedAt());
    }

    private ReviewFormDto toDto(ReviewForm form) {
        return new ReviewFormDto(form.getId(), form.getTitle(), form.getDescription(),
                readJson(form.getSchema()), form.getCreatedAt(), form.getUpdatedAt());
    }

    private CampaignDto toDto(ReviewCampaign c) {
        return new CampaignDto(c.getId(), c.getTitle(), c.getType(), c.getForm().getId(),
            c.getFormTitle(), c.getStartDate(), c.getEndDate(), c.getStatus(), c.getCreatedAt(),
            readJson(c.getFormSchema()));
    }

    private SubmissionDto toDto(ReviewSubmission s) {
        return new SubmissionDto(s.getId(), s.getCampaign().getId(), s.getUserId(),
                userName(s.getUserId()), readJson(s.getAnswers()), s.getStatus(),
                s.getSubmittedAt(), s.getUpdatedAt());
    }

    // ------------------------------------------------------------------ Helpers

    private ReferenceDocument requireReference(UUID id) {
        return referenceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document de référence introuvable."));
    }

    private ReviewForm requireForm(UUID id) {
        return formRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Modèle de formulaire introuvable."));
    }

    private ReviewCampaign requireCampaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Campagne introuvable."));
    }

    private String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Le titre est requis.");
        }
        return trimmed;
    }

    private String writeJson(JsonNode node) {
        try {
            String json = node == null || node.isNull()
                    ? mapper.createObjectNode().toString()
                    : mapper.writeValueAsString(node);
            if (json.length() > MAX_JSON_CHARS) {
                throw new BadRequestException("Le contenu est trop volumineux.");
            }
            return json;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BadRequestException("Contenu JSON invalide.");
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return mapper.createObjectNode();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID currentUserId() {
        return SecurityUtils.requirePrincipal().getUserId();
    }

    private UUID currentInternalUserId() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.getAuthorityNames().contains("ROLE_INTERNAL")) {
            throw new AccessDeniedException("Review submissions are available to internal employees only.");
        }
        return principal.getUserId();
    }

    private String currentUserName() {
        return userName(currentUserId());
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId)
                .map(this::formatName)
                .orElse("Utilisateur");
    }

    private String formatName(User user) {
        String name = (safe(user.getFirstName()) + " " + safe(user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isManager() {
        return SecurityUtils.getCurrentPrincipal()
                .map(UserPrincipal::getAuthorityNames)
                .map(names -> names.contains("ROLE_ADMIN") || names.contains("ROLE_BUSINESS_MANAGER"))
                .orElse(false);
    }

    private void requireManager() {
        if (!isManager()) {
            throw new AccessDeniedException("Action réservée aux ingénieurs d'affaires et administrateurs.");
        }
    }
}
