package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.ConflictException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.domain.cra.CraProject;
import com.esn.inodoc.domain.cra.CraProjectType;
import com.esn.inodoc.dto.cra.CraCollaboratorDto;
import com.esn.inodoc.dto.cra.CraProjectDto;
import com.esn.inodoc.dto.cra.CraProjectRequest;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.repo.cra.CraProjectRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.service.CraFeatureService;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CraProjectService {
    private static final List<DefaultProject> DEFAULT_PROJECTS = List.of(
            new DefaultProject("INTERNAL_TRAINING", "Formation interne",
                "Temps consacré aux formations et au développement des compétences internes."),
            new DefaultProject("PAID_LEAVE", "Congés payés", "Jours d'absence pris au titre des congés payés."),
            new DefaultProject("UNPAID_LEAVE", "Congé sans solde", "Jours d'absence non rémunérés."),
            new DefaultProject("RTT_EMPLOYER", "RTT employeur", "Jour de réduction du temps de travail fixé par l'employeur."),
            new DefaultProject("RTT_EMPLOYEE", "RTT salarié", "Jour de réduction du temps de travail choisi par le salarié."),
            new DefaultProject("SICK_LEAVE", "Arrêt maladie", "Période d'absence déclarée au titre d'un arrêt de travail."),
            new DefaultProject("MATERNITY_PATERNITY_LEAVE", "Congé maternité / paternité",
                "Période de congé liée à une naissance ou à une adoption."),
            new DefaultProject("PARENTAL_LEAVE", "Congé parental", "Période de congé parental d'éducation."),
            new DefaultProject("FAMILY_EVENT_LEAVE", "Congé pour événement familial",
                "Absence autorisée pour un événement familial."),
            new DefaultProject("AUTHORIZED_ABSENCE", "Absence autorisée", "Absence validée par l'employeur."),
            new DefaultProject("PUBLIC_HOLIDAY", "Jour férié", "Jour férié non travaillé selon le calendrier applicable."));

    private final CraProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final CraFeatureService craFeatureService;

    public CraProjectService(CraProjectRepository projectRepository, UserRepository userRepository,
                             CraFeatureService craFeatureService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.craFeatureService = craFeatureService;
    }

    @Transactional
    public void ensureDefaultProjects() {
        DEFAULT_PROJECTS.forEach(defaultProject -> projectRepository.findByCode(defaultProject.code())
                .ifPresentOrElse(project -> {
                    if (project.isArchived()) return;
                    if (project.getDescription() == null || project.getDescription().isBlank()) {
                        project.setDescription(defaultProject.description());
                        projectRepository.save(project);
                    }
                }, () -> {
                    CraProject project = new CraProject();
                    project.setCode(defaultProject.code());
                    project.setName(defaultProject.name());
                    project.setDescription(defaultProject.description());
                    project.setType(CraProjectType.ACCESS_ALL);
                    project.setSystemDefined(true);
                    projectRepository.save(project);
                }));
    }

    @Transactional(readOnly = true)
    public List<CraProjectDto> listForManagement() {
        return projectRepository.findAllByOrderByTypeAscNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CraCollaboratorDto> listCollaborators() {
        return userRepository.findEnabledWithRole(RoleName.INTERNAL).stream()
                .map(this::toCollaboratorDto).toList();
    }

    @Transactional
    public CraProjectDto createProject(CraProjectRequest request) {
        CraProjectType type = request.type();
        if (type != CraProjectType.CLIENT && type != CraProjectType.ACCESS_ALL) {
            throw new BadRequestException("Sélectionnez un type de projet valide");
        }
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCaseAndType(name, type)) {
            throw new ConflictException("Un projet de ce type porte déjà ce nom");
        }
        rejectCollaboratorsForProjectCreation(request.collaboratorIds());
        CraProject project = new CraProject();
        project.setCode(type.name() + "_" + UUID.randomUUID());
        project.setName(name);
        project.setDescription(trimToNull(request.description()));
        project.setType(type);
        project.setSystemDefined(false);
        project.setClientName(type == CraProjectType.CLIENT ? trimToNull(request.clientName()) : null);
        project.setCollaborators(Set.of());
        return toDto(projectRepository.save(project));
    }

    @Transactional
    public CraProjectDto updateProject(UUID id, CraProjectRequest request) {
        CraProject project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Projet introuvable"));
        if (project.isArchived()) throw new NotFoundException("Projet introuvable");
        String name = request.name().trim();
        CraProjectType type = request.type() == null ? project.getType() : request.type();
        if (project.isSystemDefined() && type != project.getType()) {
            throw new BadRequestException("Le type d'une activité par défaut ne peut pas être modifié");
        }
        if (!project.isSystemDefined() && type != CraProjectType.CLIENT && type != CraProjectType.ACCESS_ALL) {
            throw new BadRequestException("Sélectionnez un type de projet valide");
        }
        if (projectRepository.existsByNameIgnoreCaseAndType(name, type)
                && !project.getName().equalsIgnoreCase(name)) {
            throw new ConflictException("Un projet porte déjà ce nom");
        }
        project.setName(name);
        project.setDescription(trimToNull(request.description()));
        project.setType(type);
        if (type == CraProjectType.CLIENT) {
            if (request.clientName() != null) project.setClientName(trimToNull(request.clientName()));
            if (request.collaboratorIds() != null) {
                project.setCollaborators(resolveCollaborators(request.collaboratorIds()));
            }
        } else {
            project.setClientName(null);
            rejectCollaboratorsForSharedProject(request.collaboratorIds());
            project.setCollaborators(Set.of());
        }
        return toDto(projectRepository.save(project));
    }

    @Transactional
    public void deleteCustomProject(UUID id) {
        CraProject project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Projet introuvable"));
        if (project.isArchived()) return;
        project.setArchived(true);
        projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<CraProjectDto> availableForCurrentCollaborator() {
        if (!craFeatureService.isEnabled()) {
            throw new NotFoundException("La fonctionnalité CRA est désactivée");
        }
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return projectRepository.findAvailableForUser(principal.getUserId(), CraProjectType.CLIENT)
                .stream().map(this::toDto).toList();
    }

    private void rejectCollaboratorsForSharedProject(Set<UUID> ids) {
        if (ids != null && !ids.isEmpty()) {
            throw new BadRequestException("Un projet accessible à tous ne peut pas avoir d'affectations");
        }
    }

    private void rejectCollaboratorsForProjectCreation(Set<UUID> ids) {
        if (ids != null && !ids.isEmpty()) {
            throw new BadRequestException("Les collaborateurs peuvent être affectés après la création du projet");
        }
    }

    private Set<User> resolveCollaborators(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Map<UUID, User> eligible = userRepository.findEnabledWithRole(RoleName.INTERNAL).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        if (!eligible.keySet().containsAll(ids)) {
            throw new BadRequestException("Seuls les collaborateurs internes actifs peuvent être affectés");
        }
        return ids.stream().map(eligible::get).collect(Collectors.toCollection(HashSet::new));
    }

    private CraProjectDto toDto(CraProject project) {
        return new CraProjectDto(project.getId(), project.getCode(), project.getName(), project.getDescription(),
            project.getType(),
                project.isSystemDefined(), project.getCollaborators().stream()
                .map(this::toCollaboratorDto)
                .sorted((left, right) -> left.lastName().compareToIgnoreCase(right.lastName()))
                .toList(), project.getClientName());
    }

    private CraCollaboratorDto toCollaboratorDto(User user) {
        return new CraCollaboratorDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record DefaultProject(String code, String name, String description) {
    }
}