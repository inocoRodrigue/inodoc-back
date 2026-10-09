package com.esn.inodoc.controller.review;

import com.esn.inodoc.dto.review.GenerationResultDto;
import com.esn.inodoc.service.review.ReviewGenerationService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generates a review form schema or a consultative document from an uploaded
 * PDF/Word file. Reserved to business managers and administrators.
 */
@RestController
@RequestMapping("/reviews/generate")
@PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_MANAGER')")
public class ReviewGenerationController {

    private final ReviewGenerationService service;

    public ReviewGenerationController(ReviewGenerationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GenerationResultDto generate(@RequestPart("file") MultipartFile file,
                                        @RequestParam("kind") String kind) {
        return service.generate(file, kind);
    }
}
