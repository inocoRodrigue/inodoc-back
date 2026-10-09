package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.repo.CompanySettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanySettingsServiceTest {
    private final CompanySettingsRepository repository = mock(CompanySettingsRepository.class);
    private final CompanySettingsService service = new CompanySettingsService(repository);

    @Test
    void returnsTwelveMonthDefaultWhenSettingsAreMissing() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        assertEquals(12, service.get().staleDossierMonths());
    }

    @Test
    void persistsConfiguredObsolescenceThreshold() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyContactDto saved = service.update(new CompanyContactDto(null, null, null, null, null, 6));

        assertEquals(6, saved.staleDossierMonths());
        verify(repository).save(argThat(settings -> settings.getStaleDossierMonths() == 6));
    }

    @Test
    void defaultsOmittedThresholdToTwelveMonthsForOlderClients() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyContactDto saved = service.update(new CompanyContactDto(null, null, null, null, null, null));

        assertEquals(12, saved.staleDossierMonths());
    }

    @Test
    void rejectsOutOfRangeObsolescenceThresholds() {
        assertThrows(BadRequestException.class,
                () -> service.update(new CompanyContactDto(null, null, null, null, null, 0)));
        assertThrows(BadRequestException.class,
                () -> service.update(new CompanyContactDto(null, null, null, null, null, 121)));
    }
}