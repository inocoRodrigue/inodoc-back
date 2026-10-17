package com.esn.inodoc.controller;

import com.esn.inodoc.service.MediaService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/media")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    public record UploadResponse(String url) {}

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER', 'FINANCE_MANAGER', 'INTERNAL', 'EXTERNAL')")
    public UploadResponse uploadImage(@RequestPart("file") MultipartFile file) {
        return new UploadResponse(service.uploadImage(file));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public UploadResponse uploadAccountPhoto(@RequestPart("file") MultipartFile file) {
        return new UploadResponse(service.uploadAccountPhoto(file));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public UploadResponse upload(@RequestParam UUID profileId, @RequestPart("file") MultipartFile file) {
        return new UploadResponse(service.upload(profileId, file));
    }
}
