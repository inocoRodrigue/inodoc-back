package com.esn.inodoc.controller.review;

import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.dto.share.CreatePrivateShareRequest;
import com.esn.inodoc.service.review.ReviewSubmissionSharingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reviews")
@PreAuthorize("isAuthenticated()")
public class ReviewShareController {
    private final ReviewSubmissionSharingService sharingService;

    public ReviewShareController(ReviewSubmissionSharingService sharingService) {
        this.sharingService = sharingService;
    }

    @GetMapping("/campaigns/{campaignId}/my-submission/shares")
    public List<BusinessManagerDto> listShares(@PathVariable UUID campaignId) {
        return sharingService.listMyAnnualSubmissionShares(campaignId);
    }

    @PostMapping("/campaigns/{campaignId}/my-submission/shares")
    public ResponseEntity<BusinessManagerDto> share(@PathVariable UUID campaignId,
                                                     @Valid @RequestBody CreatePrivateShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sharingService.shareMyAnnualSubmission(campaignId, request.businessManagerId()));
    }

    @DeleteMapping("/campaigns/{campaignId}/my-submission/shares/{businessManagerId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID campaignId, @PathVariable UUID businessManagerId) {
        sharingService.revokeMyAnnualSubmissionShare(campaignId, businessManagerId);
        return ResponseEntity.noContent().build();
    }
}