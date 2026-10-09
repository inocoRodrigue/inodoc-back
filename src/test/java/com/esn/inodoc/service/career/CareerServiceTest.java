package com.esn.inodoc.service.career;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.career.CareerEntry;
import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.dto.career.CareerEntryRequest;
import com.esn.inodoc.dto.career.CareerTemplateRequest;
import com.esn.inodoc.repo.career.CareerEntryRepository;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CareerServiceTest {
    private final CareerTemplateRepository templates = mock(CareerTemplateRepository.class);
    private final CareerEntryRepository entries = mock(CareerEntryRepository.class);
    private final CareerService service = new CareerService(templates, entries, new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalUserCannotEditTemplate() {
        signIn(RoleName.INTERNAL);
        assertThrows(AccessDeniedException.class, () -> service.updateTemplate(
                new CareerTemplateRequest("Carnet", null, null)));
        verify(templates, never()).save(any());
    }

    @Test
    void managerCanEditTemplate() {
        UUID uid = signIn(RoleName.BUSINESS_MANAGER);
        when(templates.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(templates.save(any())).thenAnswer(call -> call.getArgument(0));
        var dto = service.updateTemplate(new CareerTemplateRequest("Carnet", "desc", null));
        assertEquals("Carnet", dto.title());
        verify(templates).save(any());
    }

    @Test
    void entryIsScopedToItsOwner() {
        UUID uid = signIn(RoleName.INTERNAL);
        UUID entryId = UUID.randomUUID();
        when(entries.findByIdAndUserId(entryId, uid)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getMyEntry(entryId));
    }

    @Test
    void allEmployeesReadTheCurrentCareerTemplateWithoutChangingTheirEntries() throws Exception {
        CareerTemplate template = new CareerTemplate();
        template.setTitle("Current template");
        template.setEntrySchema("{\"sections\":[]}");
        when(templates.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(template));
        when(templates.save(any())).thenAnswer(call -> call.getArgument(0));
        signIn(RoleName.BUSINESS_MANAGER);
        service.updateTemplate(new CareerTemplateRequest("Updated template", null,
                new ObjectMapper().readTree("{\"sections\":[{\"id\":\"new-section\"}]}")));
        for (int employee = 0; employee < 2; employee++) {
            signIn(RoleName.INTERNAL);
            var current = service.getTemplate();
            assertEquals("Updated template", current.title());
            assertEquals("new-section", current.entrySchema().path("sections").get(0).path("id").asText());
        }
        verifyNoInteractions(entries);
    }

    @Test
    void externalUserCannotReadOrCreateCareerEntries() {
        signIn(RoleName.EXTERNAL);
        assertThrows(AccessDeniedException.class, service::listMyEntries);
        assertThrows(AccessDeniedException.class, () -> service.createMyEntry(
                new CareerEntryRequest("Private entry", null, null)));
        verify(entries, never()).save(any());
    }

    @Test
    void createEntryStampsTheCurrentUser() {
        UUID uid = signIn(RoleName.INTERNAL);
        when(entries.save(any())).thenAnswer(call -> call.getArgument(0));
        service.createMyEntry(new CareerEntryRequest("Bilan 2026", null, null));
        verify(entries).save(argThat((CareerEntry e) -> uid.equals(e.getUserId())
                && "Bilan 2026".equals(e.getTitle())));
    }

    private UUID signIn(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Léa");
        user.setLastName("Ko");
        user.setRoles(Set.of(role));
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return user.getId();
    }
}
