package com.esn.inodoc.controller;

import com.esn.inodoc.dto.profile.ResumeExtraction;
import com.esn.inodoc.service.ResumeIngestionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/profile")
public class ResumeController {
    private final ResumeIngestionService service;

    public ResumeController(ResumeIngestionService service) {
        this.service = service;
    }

    @PostMapping(value = "/ingest-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResumeExtraction ingest(@RequestPart("file") MultipartFile file) {
        return service.ingest(file);
    }
}
