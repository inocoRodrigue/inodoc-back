package com.esn.inodoc.service;

import com.esn.inodoc.domain.Permission;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.repo.PermissionRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.security.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provisioning of reference data: global permissions and
 * default application roles.
 */
@Service
public class ProvisioningService {

    private static final Map<String, String> PERMISSION_CATALOG = new LinkedHashMap<>();

    static {
        PERMISSION_CATALOG.put(Permissions.USER_READ, "Consulter les utilisateurs");
        PERMISSION_CATALOG.put(Permissions.USER_MANAGE, "Gérer les utilisateurs et rôles");
        PERMISSION_CATALOG.put(Permissions.PROFILE_READ, "Consulter les dossiers de compétences");
        PERMISSION_CATALOG.put(Permissions.PROFILE_WRITE, "Créer et modifier les dossiers de compétences");
        PERMISSION_CATALOG.put(Permissions.SHARE_MANAGE, "Gérer les partages sécurisés");
        PERMISSION_CATALOG.put(Permissions.DOCUMENT_GENERATE, "Générer les documents (CV, dossier PDF)");
        PERMISSION_CATALOG.put(Permissions.SETTINGS_MANAGE, "Configurer les informations de l'entreprise");
    }

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public ProvisioningService(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void ensureGlobalPermissions() {
        PERMISSION_CATALOG.forEach((code, description) -> {
            if (permissionRepository.findByCode(code).isEmpty()) {
                Permission permission = new Permission();
                permission.setCode(code);
                permission.setDescription(description);
                permissionRepository.save(permission);
            }
        });
    }

    @Transactional
    public void createDefaultRoles() {
        ensureGlobalPermissions();
        createRole(RoleName.ADMIN, "Administrateur", allPermissionCodes());
        createRole(RoleName.BUSINESS_MANAGER, "Ingenieur d'affaires", Set.of(
                Permissions.USER_READ, Permissions.PROFILE_READ, Permissions.PROFILE_WRITE,
                Permissions.SHARE_MANAGE, Permissions.DOCUMENT_GENERATE,
                Permissions.SETTINGS_MANAGE));
        createRole(RoleName.INTERNAL, "Collaborateur interne", Set.of(
                Permissions.PROFILE_READ, Permissions.PROFILE_WRITE, Permissions.DOCUMENT_GENERATE));
        createRole(RoleName.EXTERNAL, "Candidat externe", Set.of(
                Permissions.PROFILE_READ, Permissions.PROFILE_WRITE));
    }

    private void createRole(RoleName name, String description, Set<String> permissionCodes) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        Set<Permission> permissions = new HashSet<>();
        permissionCodes.forEach(code -> permissionRepository.findByCode(code).ifPresent(permissions::add));
        role.setPermissions(permissions);
        roleRepository.save(role);
    }

    private Set<String> allPermissionCodes() {
        return new HashSet<>(PERMISSION_CATALOG.keySet());
    }
}
