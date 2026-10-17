package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.repo.CompanySettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraFeatureServiceTest {
    private final CompanySettingsRepository repository = mock(CompanySettingsRepository.class);
    private final CraFeatureService service = new CraFeatureService(repository);

    @Test
    void defaultsToDisabledWhenNoSettingsExist() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertFalse(service.status().enabled());
        assertFalse(service.isEnabled());
    }

    @Test
    void persistsBothEnableAndDisableChanges() {
        CompanySettings settings = new CompanySettings();
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(settings));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertTrue(service.setEnabled(true).enabled());
        assertTrue(settings.isCraEnabled());
        assertFalse(service.setEnabled(false).enabled());
        assertFalse(settings.isCraEnabled());
        verify(repository, org.mockito.Mockito.times(2)).save(settings);
    }
}