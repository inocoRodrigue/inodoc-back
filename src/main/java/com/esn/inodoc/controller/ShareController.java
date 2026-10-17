package com.esn.inodoc.controller;

import com.esn.inodoc.dto.share.CreateShareRequest;
import com.esn.inodoc.dto.share.SharedLinkDto;
import com.esn.inodoc.service.ShareService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/shares")
@PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER')")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<SharedLinkDto> create(@Valid @RequestBody CreateShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shareService.create(request));
    }

    @GetMapping
    public Page<SharedLinkDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shareService.list(PageRequest.of(page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        shareService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
