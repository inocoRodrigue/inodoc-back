package com.esn.inodoc.service.career;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.controller.error.NotFoundException;
import com.esn.inodoc.domain.career.CareerEntry;
import com.esn.inodoc.domain.career.CareerTemplate;
import com.esn.inodoc.dto.career.CareerEntryDto;
import com.esn.inodoc.dto.career.CareerEntryRequest;
import com.esn.inodoc.dto.career.CareerTemplateDto;
import com.esn.inodoc.dto.career.CareerTemplateRequest;
import com.esn.inodoc.repo.career.CareerEntryRepository;
import com.esn.inodoc.repo.career.CareerTemplateRepository;
import com.esn.inodoc.security.UserPrincipal;
import com.esn.inodoc.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Career-log module: a single shared template (edited by business managers)
 * and personal entries kept by each employee over the years. Entries are
 * strictly scoped to their owner.
 */
@Service
public class CareerService {

    private static final int MAX_JSON_CHARS = 200_000;

    private final CareerTemplateRepository templateRepository;
    private final CareerEntryRepository entryRepository;
    private final ObjectMapper mapper;

    public CareerService(CareerTemplateRepository templateRepository,
                         CareerEntryRepository entryRepository,
                         ObjectMapper mapper) {
        this.templateRepository = templateRepository;
        this.entryRepository = entryRepository;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- Template

    @Transactional(readOnly = true)
    public CareerTemplateDto getTemplate() {
        return templateRepository.findFirstByOrderByCreatedAtAsc()
                .map(this::toDto)
                .orElseGet(() -> new CareerTemplateDto(null, "Career journal", null,
                        mapper.createObjectNode().<com.fasterxml.jackson.databind.node.ObjectNode>set(
                                "sections", mapper.createArrayNode()), null));
    }

    @Transactional
    public CareerTemplateDto updateTemplate(CareerTemplateRequest request) {
        requireManager();
        CareerTemplate template = templateRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(CareerTemplate::new);
        template.setTitle(requireTitle(request.title()));
        template.setDescription(trimToNull(request.description()));
        template.setEntrySchema(writeJson(request.entrySchema()));
        if (template.getCreatedBy() == null) {
            template.setCreatedBy(currentUserId());
        }
        return toDto(templateRepository.save(template));
    }

    // -------------------------------------------------------------- Entries

    @Transactional(readOnly = true)
    public List<CareerEntryDto> listMyEntries() {
        return entryRepository.findByUserIdOrderByEntryDateDescCreatedAtDesc(currentInternalUserId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CareerEntryDto getMyEntry(UUID id) {
        return toDto(requireOwnedEntry(id));
    }

    @Transactional
    public CareerEntryDto createMyEntry(CareerEntryRequest request) {
        CareerEntry entry = new CareerEntry();
        entry.setUserId(currentInternalUserId());
        apply(entry, request);
        return toDto(entryRepository.save(entry));
    }

    @Transactional
    public CareerEntryDto updateMyEntry(UUID id, CareerEntryRequest request) {
        CareerEntry entry = requireOwnedEntry(id);
        apply(entry, request);
        return toDto(entryRepository.save(entry));
    }

    @Transactional
    public void deleteMyEntry(UUID id) {
        entryRepository.delete(requireOwnedEntry(id));
    }

    private void apply(CareerEntry entry, CareerEntryRequest request) {
        entry.setTitle(requireTitle(request.title()));
        entry.setEntryDate(request.entryDate());
        entry.setData(writeJson(request.data()));
    }

    // -------------------------------------------------------------- Mappers

    private CareerTemplateDto toDto(CareerTemplate t) {
        return new CareerTemplateDto(t.getId(), t.getTitle(), t.getDescription(),
                readJson(t.getEntrySchema()), t.getUpdatedAt());
    }

    private CareerEntryDto toDto(CareerEntry e) {
        return new CareerEntryDto(e.getId(), e.getTitle(), e.getEntryDate(),
                readJson(e.getData()), e.getCreatedAt(), e.getUpdatedAt());
    }

    // -------------------------------------------------------------- Helpers

    private CareerEntry requireOwnedEntry(UUID id) {
        return entryRepository.findByIdAndUserId(id, currentInternalUserId())
                .orElseThrow(() -> new NotFoundException("Entrée de carnet introuvable."));
    }

    private UUID currentInternalUserId() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!principal.getAuthorityNames().contains("ROLE_INTERNAL")) {
            throw new AccessDeniedException("Career entries are available to internal employees only.");
        }
        return principal.getUserId();
    }

    private String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Le titre est requis.");
        }
        return trimmed;
    }

    private String writeJson(JsonNode node) {
        try {
            String json = node == null || node.isNull()
                    ? mapper.createObjectNode().toString()
                    : mapper.writeValueAsString(node);
            if (json.length() > MAX_JSON_CHARS) {
                throw new BadRequestException("Le contenu est trop volumineux.");
            }
            return json;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BadRequestException("Contenu JSON invalide.");
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return mapper.createObjectNode();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UUID currentUserId() {
        return SecurityUtils.requirePrincipal().getUserId();
    }

    private void requireManager() {
        boolean manager = SecurityUtils.getCurrentPrincipal()
                .map(UserPrincipal::getAuthorityNames)
                .map(names -> names.contains("ROLE_ADMIN") || names.contains("ROLE_BUSINESS_MANAGER"))
                .orElse(false);
        if (!manager) {
            throw new AccessDeniedException("Action réservée aux ingénieurs d'affaires et administrateurs.");
        }
    }
}
