package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private final ProfileService profiles;
    private final ProfileRepository repository;
    private final ImageValidator validator;
    private final S3ImageStorage storage;
    private final UserRepository users;

    public MediaService(ProfileService profiles, ProfileRepository repository,
                        ImageValidator validator, S3ImageStorage storage, UserRepository users) {
        this.profiles = profiles;
        this.repository = repository;
        this.validator = validator;
        this.storage = storage;
        this.users = users;
    }

    @Transactional
    public String upload(UUID profileId, MultipartFile file) {
        profiles.assertCanAccess(profileId);
        var profile = profiles.requireProfile(profileId);
        var image = validator.validate(file);
        String key = "profiles/" + profileId + "/" + UUID.randomUUID() + "." + image.extension();
        String url = storage.upload(key, image);
        cleanupOnRollback(key);
        profile.setImageUrl(url);
        repository.saveAndFlush(profile);
        return url;
    }

    @Transactional
    public String uploadAccountPhoto(MultipartFile file) {
        var user = users.findById(SecurityUtils.requirePrincipal().getUserId())
                .filter(account -> account.isEnabled())
                .orElseThrow(() -> new AccessDeniedException("Compte utilisateur indisponible"));
        var image = validator.validate(file);
        String key = "users/" + user.getId() + "/" + UUID.randomUUID() + "." + image.extension();
        String url = storage.upload(key, image);
        cleanupOnRollback(key);
        user.setImageUrl(url);
        if (user.getProfile() != null) {
            user.getProfile().setImageUrl(url);
            repository.saveAndFlush(user.getProfile());
        }
        users.saveAndFlush(user);
        return url;
    }

    /**
     * Stores a standalone dossier image (certification badge, diploma, test
    * capture...) on S3 and returns a signed preview URL. The dossier mapper
    * removes the temporary signature before persistence. An orphaned object
    * is tolerated if the dossier is never saved.
     */
    public String uploadImage(MultipartFile file) {
        var image = validator.validate(file);
        String key = "dossier-assets/" + UUID.randomUUID() + "." + image.extension();
        return storage.signedUrl(storage.upload(key, image));
    }

    private void cleanupOnRollback(String key) {
        // S3 is not transactional: remove the new object if the database save rolls back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storage.delete(key);
                    } catch (IntegrationUnavailableException ex) {
                        log.error("Failed to clean up rolled-back profile image {}", key);
                    }
                }
            }
        });
    }
}
