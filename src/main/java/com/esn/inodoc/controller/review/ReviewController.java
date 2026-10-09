package com.esn.inodoc.controller.review;

import com.esn.inodoc.dto.review.CampaignDto;
import com.esn.inodoc.dto.review.CampaignRequest;
import com.esn.inodoc.dto.review.CampaignStatusRequest;
import com.esn.inodoc.dto.review.ReferenceDocumentDto;
import com.esn.inodoc.dto.review.ReferenceDocumentRequest;
import com.esn.inodoc.dto.review.ReviewFormDto;
import com.esn.inodoc.dto.review.ReviewFormRequest;
import com.esn.inodoc.dto.review.SubmissionDto;
import com.esn.inodoc.dto.review.SubmissionRequest;
import com.esn.inodoc.service.review.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Review module API: consultative reference documents, fillable form
 * templates, campaigns and submissions. Fine-grained rules (manager vs
 * employee) are enforced in {@link ReviewService}.
 */
@RestController
@RequestMapping("/reviews")
@PreAuthorize("isAuthenticated()")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    // References (consultative, read-only for employees)

    @GetMapping("/references")
    public List<ReferenceDocumentDto> listReferences() {
        return service.listReferences();
    }

    @GetMapping("/references/{id}")
    public ReferenceDocumentDto getReference(@PathVariable UUID id) {
        return service.getReference(id);
    }

    @PostMapping("/references")
    public ResponseEntity<ReferenceDocumentDto> createReference(@Valid @RequestBody ReferenceDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createReference(request));
    }

    @PutMapping("/references/{id}")
    public ReferenceDocumentDto updateReference(@PathVariable UUID id,
                                                @Valid @RequestBody ReferenceDocumentRequest request) {
        return service.updateReference(id, request);
    }

    @DeleteMapping("/references/{id}")
    public ResponseEntity<Void> deleteReference(@PathVariable UUID id) {
        service.deleteReference(id);
        return ResponseEntity.noContent().build();
    }

    // Form templates (managed by business managers)

    @GetMapping("/forms")
    public List<ReviewFormDto> listForms() {
        return service.listForms();
    }

    @GetMapping("/forms/{id}")
    public ReviewFormDto getForm(@PathVariable UUID id) {
        return service.getForm(id);
    }

    @PostMapping("/forms")
    public ResponseEntity<ReviewFormDto> createForm(@Valid @RequestBody ReviewFormRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createForm(request));
    }

    @PutMapping("/forms/{id}")
    public ReviewFormDto updateForm(@PathVariable UUID id, @Valid @RequestBody ReviewFormRequest request) {
        return service.updateForm(id, request);
    }

    @DeleteMapping("/forms/{id}")
    public ResponseEntity<Void> deleteForm(@PathVariable UUID id) {
        service.deleteForm(id);
        return ResponseEntity.noContent().build();
    }

    // Campaigns

    @GetMapping("/campaigns")
    public List<CampaignDto> listCampaigns() {
        return service.listCampaigns();
    }

    @GetMapping("/campaigns/{id}")
    public CampaignDto getCampaign(@PathVariable UUID id) {
        return service.getCampaign(id);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<CampaignDto> createCampaign(@Valid @RequestBody CampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCampaign(request));
    }

    @PutMapping("/campaigns/{id}/status")
    public CampaignDto updateCampaignStatus(@PathVariable UUID id,
                                            @Valid @RequestBody CampaignStatusRequest request) {
        return service.updateCampaignStatus(id, request.status());
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable UUID id) {
        service.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    // Submissions

    @GetMapping("/campaigns/{campaignId}/my-submission")
    public SubmissionDto getMySubmission(@PathVariable UUID campaignId) {
        return service.getMySubmission(campaignId);
    }

    @PutMapping("/campaigns/{campaignId}/my-submission")
    public SubmissionDto saveMySubmission(@PathVariable UUID campaignId,
                                          @RequestBody SubmissionRequest request) {
        return service.saveMySubmission(campaignId, request);
    }

    @GetMapping("/campaigns/{campaignId}/submissions")
    public List<SubmissionDto> listCampaignSubmissions(@PathVariable UUID campaignId) {
        return service.listCampaignSubmissions(campaignId);
    }

    @GetMapping("/submissions/{id}")
    public SubmissionDto getSubmission(@PathVariable UUID id) {
        return service.getSubmission(id);
    }
}
