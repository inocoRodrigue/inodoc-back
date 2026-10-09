package com.esn.inodoc.controller;

import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.share.SharedDossierView;
import com.esn.inodoc.dto.share.SharedEditRequest;
import com.esn.inodoc.service.ShareService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public access to share links (without authentication).
 */
@RestController
@RequestMapping("/public/share")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    public SharedDossierView view(@PathVariable String token,
                                  @RequestParam(required = false) String password) {
        return shareService.access(token, password);
    }

    @PutMapping("/{token}")
    public DossierDto edit(@PathVariable String token, @Valid @RequestBody SharedEditRequest request) {
        return shareService.editViaShare(token, request);
    }
}
