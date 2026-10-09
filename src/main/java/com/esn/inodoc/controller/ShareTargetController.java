package com.esn.inodoc.controller;

import com.esn.inodoc.dto.share.BusinessManagerDto;
import com.esn.inodoc.service.BusinessManagerDirectoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/share-targets")
@PreAuthorize("isAuthenticated()")
public class ShareTargetController {
    private final BusinessManagerDirectoryService directoryService;

    public ShareTargetController(BusinessManagerDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/business-managers")
    public List<BusinessManagerDto> listBusinessManagers() {
        return directoryService.listForCurrentInternalUser();
    }
}