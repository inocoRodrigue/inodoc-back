package com.esn.inodoc.service;

import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.repo.RoleRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisioningServiceTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ProvisioningService service = new ProvisioningService(roles);

    @Test
    void provisionsAllRolesIncludingFinanceManagerIdempotently() {
        Map<RoleName, Role> storedRoles = new EnumMap<>(RoleName.class);
        when(roles.findByName(any())).thenAnswer(call -> Optional.ofNullable(storedRoles.get(call.getArgument(0))));
        when(roles.save(any(Role.class))).thenAnswer(call -> {
            Role role = call.getArgument(0);
            storedRoles.put(role.getName(), role);
            return role;
        });

        service.createDefaultRoles();
        service.createDefaultRoles();

        assertEquals(EnumSet.allOf(RoleName.class), storedRoles.keySet());
        verify(roles, times(RoleName.values().length)).save(any(Role.class));
    }
}