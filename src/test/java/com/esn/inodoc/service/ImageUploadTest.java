package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageUploadTest {
    @Test
    void validatesRealContentRatherThanMimeHeader() throws Exception {
        var validator = new ImageValidator();
        assertThrows(BadRequestException.class, () -> validator.validate(
                new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3})));
        assertThrows(BadRequestException.class, () -> validator.validate(
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])));
        var image = validator.validate(png());
        assertEquals("image/png", image.contentType());
    }

    @Test
    void standaloneImageUploadStoresAndReturnsUrlWithoutTouchingDatabase() throws Exception {
        var storage = mock(S3ImageStorage.class);
        var repository = mock(ProfileRepository.class);
        var users = mock(UserRepository.class);
        when(storage.upload(anyString(), any())).thenReturn("https://bucket.s3.amazonaws.com/dossier-assets/x.png");
        when(storage.signedUrl("https://bucket.s3.amazonaws.com/dossier-assets/x.png"))
            .thenReturn("https://bucket.s3.amazonaws.com/dossier-assets/x.png?X-Amz-Signature=test");
        var service = new MediaService(mock(ProfileService.class), repository, new ImageValidator(), storage, users);
        String url = service.uploadImage(png());
        assertEquals("https://bucket.s3.amazonaws.com/dossier-assets/x.png?X-Amz-Signature=test", url);
        verify(storage).upload(startsWith("dossier-assets/"), any());
        verify(storage).signedUrl("https://bucket.s3.amazonaws.com/dossier-assets/x.png");
        verifyNoInteractions(repository, users);
    }

    @Test
    void unauthorizedProfileNeverReachesStorage() throws Exception {
        var profiles = mock(ProfileService.class);
        var storage = mock(S3ImageStorage.class);
        var repository = mock(ProfileRepository.class);
        UUID id = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(profiles).assertCanAccess(id);
        var service = new MediaService(profiles, repository, new ImageValidator(), storage, mock(UserRepository.class));
        assertThrows(AccessDeniedException.class, () -> service.upload(id, png()));
        verifyNoInteractions(storage, repository);
    }

    @Test
    void storesReturnedUrlAndCleansUpOnRollback() throws Exception {
        var profiles = mock(ProfileService.class);
        var storage = mock(S3ImageStorage.class);
        var repository = mock(ProfileRepository.class);
        var profile = new Profile();
        UUID id = UUID.randomUUID();
        when(profiles.requireProfile(id)).thenReturn(profile);
        when(storage.upload(anyString(), any())).thenReturn("https://test-bucket.s3.amazonaws.com/image.png");
        var service = new MediaService(profiles, repository, new ImageValidator(), storage, mock(UserRepository.class));
        TransactionSynchronizationManager.initSynchronization();
        try {
            String url = service.upload(id, png());
            assertEquals(url, profile.getImageUrl());
            verify(repository).saveAndFlush(profile);
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(storage).delete(startsWith("profiles/" + id + "/"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void accountPhotoWorksWithoutDossierAndSynchronizesLinkedProfile() throws Exception {
        var storage = mock(S3ImageStorage.class);
        var profiles = mock(ProfileRepository.class);
        var users = mock(UserRepository.class);
        var user = new User();
        user.setId(UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(storage.upload(anyString(), any())).thenReturn("https://example.test/photo.png");
        var principal = com.esn.inodoc.security.UserPrincipal.from(user);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        var service = new MediaService(mock(ProfileService.class), profiles, new ImageValidator(), storage, users);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("https://example.test/photo.png", service.uploadAccountPhoto(png()));
            assertEquals("https://example.test/photo.png", user.getImageUrl());
            verifyNoInteractions(profiles);
            user.setProfile(new Profile());
            service.uploadAccountPhoto(png());
            assertEquals(user.getImageUrl(), user.getProfile().getImageUrl());
            verify(profiles).saveAndFlush(user.getProfile());
            verify(users, times(2)).saveAndFlush(user);
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(storage, times(2)).delete(startsWith("users/" + user.getId() + "/"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private MockMultipartFile png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "test.png", "image/png", output.toByteArray());
    }
}
