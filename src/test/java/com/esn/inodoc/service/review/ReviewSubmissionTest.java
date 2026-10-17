package com.esn.inodoc.service.review;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.review.CampaignStatus;
import com.esn.inodoc.domain.review.ReviewCampaign;
import com.esn.inodoc.domain.review.ReviewForm;
import com.esn.inodoc.domain.review.ReviewSubmission;
import com.esn.inodoc.domain.review.ReviewType;
import com.esn.inodoc.domain.review.SubmissionStatus;
import com.esn.inodoc.dto.review.ReferenceDocumentRequest;
import com.esn.inodoc.dto.review.CampaignRequest;
import com.esn.inodoc.dto.review.SubmissionRequest;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.review.ReferenceDocumentRepository;
import com.esn.inodoc.repo.review.ReviewCampaignRepository;
import com.esn.inodoc.repo.review.ReviewFormRepository;
import com.esn.inodoc.repo.review.ReviewSubmissionRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewSubmissionTest {
    private final ReferenceDocumentRepository references = mock(ReferenceDocumentRepository.class);
    private final ReviewFormRepository forms = mock(ReviewFormRepository.class);
    private final ReviewCampaignRepository campaigns = mock(ReviewCampaignRepository.class);
    private final ReviewSubmissionRepository submissions = mock(ReviewSubmissionRepository.class);
    private final ReviewSubmissionSharingService sharingService = mock(ReviewSubmissionSharingService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReviewService service = new ReviewService(references, forms, campaigns,
            submissions, sharingService, users, new ObjectMapper());

    private UUID userId;
    private ReviewCampaign campaign;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setFirstName("Léa");
        user.setLastName("Ko");
        user.setRoles(Set.of(role(RoleName.INTERNAL)));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        signIn(user);

        ReviewForm form = new ReviewForm();
        form.setId(UUID.randomUUID());
        campaign = new ReviewCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setForm(form);
        campaign.setStatus(CampaignStatus.OPEN);
        when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(submissions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
        void firstSaveCreatesDraftThenCanBeSubmittedAndEdited() throws Exception {
        when(submissions.findByCampaignIdAndUserId(campaign.getId(), userId))
                .thenReturn(Optional.empty());
        var draft = service.saveMySubmission(campaign.getId(),
                new SubmissionRequest(new ObjectMapper().readTree("{\"f1\":\"ok\"}"), false));
        assertEquals(SubmissionStatus.DRAFT, draft.status());

        ReviewSubmission existing = new ReviewSubmission();
        existing.setCampaign(campaign);
        existing.setUserId(userId);
        existing.setStatus(SubmissionStatus.DRAFT);
        when(submissions.findByCampaignIdAndUserId(campaign.getId(), userId))
                .thenReturn(Optional.of(existing));
        var submitted = service.saveMySubmission(campaign.getId(),
                new SubmissionRequest(new ObjectMapper().readTree("{\"f1\":\"final\"}"), true));
        assertEquals(SubmissionStatus.SUBMITTED, submitted.status());
        assertNotNull(submitted.submittedAt());
    }

    @Test
    void submittedSubmissionCanBeEditedWhileCampaignIsOpen() throws Exception {
        ReviewSubmission submitted = new ReviewSubmission();
        submitted.setCampaign(campaign);
        submitted.setUserId(userId);
        submitted.setStatus(SubmissionStatus.SUBMITTED);
        submitted.setSubmittedAt(java.time.Instant.now());
        when(submissions.findByCampaignIdAndUserId(campaign.getId(), userId))
                .thenReturn(Optional.of(submitted));

        var draft = service.saveMySubmission(campaign.getId(), new SubmissionRequest(
                new ObjectMapper().readTree("{\"f1\":\"updated\"}"), false));

        assertEquals(SubmissionStatus.DRAFT, draft.status());
        assertNull(draft.submittedAt());
        assertEquals("updated", draft.answers().path("f1").asText());
        verify(submissions).saveAndFlush(submitted);
    }

    @Test
    void closedCampaignRejectsAnswers() {
        campaign.setStatus(CampaignStatus.CLOSED);
        assertThrows(BadRequestException.class, () -> service.saveMySubmission(campaign.getId(),
                new SubmissionRequest(null, false)));
    }

        @Test
        void campaignKeepsTheTemplateAvailableAtCreation() {
                signIn(roleUser(RoleName.BUSINESS_MANAGER));
                ReviewForm form = campaign.getForm();
                form.setTitle("Original template");
                form.setSchema("{\"sections\":[{\"id\":\"original\"}]}");
                when(forms.findById(form.getId())).thenReturn(Optional.of(form));
                when(campaigns.save(any())).thenAnswer(call -> call.getArgument(0));
                CampaignRequest request = mock(CampaignRequest.class);
                when(request.title()).thenReturn("Annual campaign");
                when(request.type()).thenReturn(ReviewType.ANNUAL);
                when(request.formId()).thenReturn(form.getId());

                var created = service.createCampaign(request);
                assertEquals("Original template", created.formTitle());
                assertEquals("original", created.formSchema().path("sections").get(0).path("id").asText());
                var captured = org.mockito.ArgumentCaptor.forClass(ReviewCampaign.class);
                verify(campaigns).save(captured.capture());
                ReviewCampaign saved = captured.getValue();
                saved.setId(UUID.randomUUID());
                when(campaigns.findById(saved.getId())).thenReturn(Optional.of(saved));

                form.setTitle("Updated template");
                form.setSchema("{\"sections\":[]}");
                for (CampaignStatus status : CampaignStatus.values()) {
                        saved.setStatus(status);
                        var existing = service.getCampaign(saved.getId());
                        assertEquals("Original template", existing.formTitle());
                        assertEquals("original", existing.formSchema().path("sections").get(0).path("id").asText());
                }
                var nextCampaign = service.createCampaign(request);
                assertEquals("Updated template", nextCampaign.formTitle());
                assertTrue(nextCampaign.formSchema().path("sections").isEmpty());
        }

        @Test
        void migrationFreezesTheCurrentTemplateForExistingCampaigns() throws Exception {
                try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
                         var statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE review_forms (id UUID PRIMARY KEY, title VARCHAR(255), schema TEXT)");
                        statement.execute("CREATE TABLE review_campaigns (id UUID PRIMARY KEY, form_id UUID NOT NULL)");
                        UUID formId = UUID.randomUUID();
                        statement.execute("INSERT INTO review_forms VALUES ('" + formId + "', 'Original', '{\"sections\":[]}')");
                        statement.execute("INSERT INTO review_campaigns VALUES ('" + UUID.randomUUID() + "', '" + formId + "')");
                        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                                        new org.springframework.core.io.ClassPathResource("db/migration/V10__campaign_form_snapshot.sql"));
                        statement.execute("UPDATE review_forms SET title = 'Changed', schema = '{}'");
                        try (var result = statement.executeQuery("SELECT form_title, form_schema FROM review_campaigns")) {
                                assertTrue(result.next());
                                assertEquals("Original", result.getString("form_title"));
                                assertEquals("{\"sections\":[]}", result.getString("form_schema"));
                        }
                }
        }

        @Test
        void businessManagerCannotReadAnUnsharedSubmission() {
                UUID managerId = UUID.randomUUID();
                User manager = new User();
                manager.setId(managerId);
                manager.setRoles(Set.of(role(RoleName.BUSINESS_MANAGER)));
                signIn(manager);

                UUID submissionId = UUID.randomUUID();
                ReviewSubmission submission = new ReviewSubmission();
                submission.setId(submissionId);
                submission.setCampaign(campaign);
                submission.setUserId(userId);
                when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
                when(sharingService.isSharedWithBusinessManager(submissionId, managerId)).thenReturn(false);

                assertThrows(AccessDeniedException.class, () -> service.getSubmission(submissionId));
        }

        @Test
        void campaignResponseListContainsOnlySharedSubmissions() {
                User manager = new User();
                manager.setId(UUID.randomUUID());
                manager.setRoles(Set.of(role(RoleName.BUSINESS_MANAGER)));
                signIn(manager);
                when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));
                when(sharingService.listSharedAnnualSubmissions(campaign.getId())).thenReturn(java.util.List.of());

                assertTrue(service.listCampaignSubmissions(campaign.getId()).isEmpty());
        }

    @Test
    void internalUserCannotManageReferencesOrListForms() {
        assertThrows(AccessDeniedException.class, () -> service.createReference(
                new ReferenceDocumentRequest("Grille", null, null, null)));
        assertThrows(AccessDeniedException.class, () -> service.listForms());
    }

        @Test
        void userWithoutAnApplicationRoleCannotReadOrSubmitAnInternalReview() {
                User user = new User();
                user.setId(UUID.randomUUID());
                signIn(user);
                assertThrows(AccessDeniedException.class, () -> service.getMySubmission(campaign.getId()));
                assertThrows(AccessDeniedException.class, () -> service.saveMySubmission(campaign.getId(),
                                new SubmissionRequest(null, false)));
        }

    private Role role(RoleName name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private void signIn(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

        private User roleUser(RoleName roleName) {
                User user = new User();
                user.setId(UUID.randomUUID());
                user.setRoles(Set.of(role(roleName)));
                return user;
        }
}
