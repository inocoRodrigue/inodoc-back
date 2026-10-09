package com.esn.inodoc.controller.career;

import com.esn.inodoc.dto.career.SharedCareerEntryDto;
import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.dto.share.CreatePrivateShareRequest;
import com.esn.inodoc.service.career.CareerEntrySharingService;
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
@RequestMapping("/career")
@PreAuthorize("isAuthenticated()")
public class CareerShareController {
    private final CareerEntrySharingService sharingService;

    public CareerShareController(CareerEntrySharingService sharingService) {
        this.sharingService = sharingService;
    }

    @GetMapping("/entries/shared-with-me")
    public List<SharedCareerEntryDto> listSharedWithMe() {
        return sharingService.listSharedWithCurrentManager();
    }

    @GetMapping("/entries/{entryId}/shares")
    public List<BusinessManagerDto> listShares(@PathVariable UUID entryId) {
        return sharingService.listShares(entryId);
    }

    @PostMapping("/entries/{entryId}/shares")
    public ResponseEntity<BusinessManagerDto> share(@PathVariable UUID entryId,
                                                     @Valid @RequestBody CreatePrivateShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sharingService.share(entryId, request.businessManagerId()));
    }

    @DeleteMapping("/entries/{entryId}/shares/{businessManagerId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID entryId, @PathVariable UUID businessManagerId) {
        sharingService.revoke(entryId, businessManagerId);
        return ResponseEntity.noContent().build();
    }
}