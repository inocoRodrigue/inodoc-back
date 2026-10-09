package com.esn.inodoc.service.career;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.career.CareerEntry;
import com.esn.inodoc.domain.career.CareerEntryShare;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.career.CareerEntryRepository;
import com.esn.inodoc.repo.career.CareerEntryShareRepository;
import com.esn.inodoc.service.BusinessManagerDirectoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerEntrySharingServiceTest {
    private final CareerEntryRepository entries = mock(CareerEntryRepository.class);
    private final CareerEntryShareRepository shares = mock(CareerEntryShareRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BusinessManagerDirectoryService directory = mock(BusinessManagerDirectoryService.class);
    private final CareerEntrySharingService service = new CareerEntrySharingService(
            entries, shares, users, directory, new ObjectMapper());

    @Test
    void ownerCanShareCareerEntryWithAnActiveBusinessManager() {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        CareerEntry entry = entry(entryId, ownerId);
        User manager = user(managerId, RoleName.BUSINESS_MANAGER);
        when(directory.requireInternalUserId()).thenReturn(ownerId);
        when(entries.findByIdAndUserId(entryId, ownerId)).thenReturn(Optional.of(entry));
        when(directory.requireActiveBusinessManager(managerId)).thenReturn(manager);
        when(shares.saveAndFlush(any(CareerEntryShare.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.share(entryId, managerId);

        assertEquals(managerId, result.id());
        verify(shares).saveAndFlush(any(CareerEntryShare.class));
    }

    @Test
    void employeeCannotShareAnotherUsersCareerEntry() {
        UUID ownerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(directory.requireInternalUserId()).thenReturn(ownerId);
        when(entries.findByIdAndUserId(entryId, ownerId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.share(entryId, UUID.randomUUID()));
        verify(shares, never()).saveAndFlush(any(CareerEntryShare.class));
    }

    @Test
    void managerReceivesOnlyEntriesSharedWithThatManager() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = user(ownerId, RoleName.INTERNAL);
        CareerEntry entry = entry(UUID.randomUUID(), ownerId);
        CareerEntryShare share = new CareerEntryShare();
        share.setCareerEntry(entry);
        when(directory.requireBusinessManagerId()).thenReturn(managerId);
        when(shares.findAllByBusinessManagerIdOrderByCreatedAtDesc(managerId)).thenReturn(List.of(share));
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));

        var result = service.listSharedWithCurrentManager();

        assertEquals(1, result.size());
        assertEquals("Employee Example", result.get(0).ownerName());
        assertEquals(entry.getId(), result.get(0).id());
        verify(shares).findAllByBusinessManagerIdOrderByCreatedAtDesc(managerId);
    }

    private CareerEntry entry(UUID id, UUID ownerId) {
        CareerEntry entry = new CareerEntry();
        entry.setId(id);
        entry.setUserId(ownerId);
        entry.setTitle("Career milestone");
        entry.setData("{\"achievement\":\"Delivery\"}");
        return entry;
    }

    private User user(UUID id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setFirstName("Employee");
        user.setLastName("Example");
        user.setEmail("employee@example.test");
        user.setRoles(Set.of(role));
        return user;
    }
}