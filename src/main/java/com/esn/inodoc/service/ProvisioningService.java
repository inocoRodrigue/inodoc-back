package com.esn.inodoc.service;

import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.repo.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisioning of default application roles.
 */
@Service
public class ProvisioningService {
    private final RoleRepository roleRepository;

    public ProvisioningService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void createDefaultRoles() {
        createRole(RoleName.ADMIN, "Administrateur");
        createRole(RoleName.BUSINESS_MANAGER, "Ingenieur d'affaires");
        createRole(RoleName.FINANCE_MANAGER, "Responsable financier");
        createRole(RoleName.INTERNAL, "Collaborateur interne");
    }

    private void createRole(RoleName name, String description) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        roleRepository.save(role);
    }
}
