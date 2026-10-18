package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.*;
import com.esn.inodoc.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraAuditServiceTest {
    final CraAuditRepository events = mock(CraAuditRepository.class);
    final CraDeclarationRepository declarations = mock(CraDeclarationRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final CraAuditService service = new CraAuditService(events, declarations, users, mapper);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    User signIn(RoleName roleName) {
        var user = new User(); user.setId(UUID.randomUUID()); user.setFirstName("Finance"); user.setLastName("Test"); user.setEmail("finance@example.test");
        var role = new Role(); role.setName(roleName); user.setRoles(Set.of(role));
        var principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user)); return user;
    }
    @Test void eventsKeepActorReasonAndImmutableBeforeAfterValues() throws Exception {
        var actor = signIn(RoleName.FINANCE_MANAGER);
        var declaration = new CraDeclaration(); declaration.setId(UUID.randomUUID()); declaration.setYear(2026); declaration.setMonth(1); declaration.setStatus(CraDeclarationStatus.DECLARED);
        String before = service.snapshot(declaration); declaration.setStatus(CraDeclarationStatus.PENDING); declaration.setRejectionReason("Projet incorrect");
        service.record(declaration, "REJECTED", "Projet incorrect", before);
        var event = org.mockito.ArgumentCaptor.forClass(CraAuditEvent.class); verify(events).save(event.capture());
        assertEquals(actor.getId(), event.getValue().getActorId()); assertEquals("Finance Test", event.getValue().getActorName());
        assertEquals("Projet incorrect", event.getValue().getReason());
        assertEquals("DECLARED", mapper.readTree(event.getValue().getBeforeState()).get("status").asText());
        assertEquals("PENDING", mapper.readTree(event.getValue().getAfterState()).get("status").asText());
        declaration.setRejectionReason("Autre motif"); assertFalse(event.getValue().getAfterState().contains("Autre motif"));
    }
    @Test void collaboratorsCannotReadAnotherUsersHistory() {
        signIn(RoleName.INTERNAL); var declaration = new CraDeclaration(); declaration.setId(UUID.randomUUID());
        var owner = new User(); owner.setId(UUID.randomUUID()); declaration.setCollaborator(owner);
        when(declarations.findById(declaration.getId())).thenReturn(Optional.of(declaration));
        assertThrows(NotFoundException.class, () -> service.history(declaration.getId(), 0, 25)); verifyNoInteractions(events);
    }
    @Test void ownersAndReviewersCanReadPagedHistory() {
        var actor = signIn(RoleName.INTERNAL); var declaration = new CraDeclaration(); declaration.setId(UUID.randomUUID()); declaration.setCollaborator(actor);
        when(declarations.findById(declaration.getId())).thenReturn(Optional.of(declaration));
        when(events.findByDeclarationIdOrderByCreatedAtDesc(eq(declaration.getId()), any())).thenReturn(new PageImpl<>(List.of()));
        assertEquals(0, service.history(declaration.getId(), 0, 25).getTotalElements());
        signIn(RoleName.FINANCE_MANAGER); assertEquals(0, service.history(declaration.getId(), 0, 25).getTotalElements());
    }
}