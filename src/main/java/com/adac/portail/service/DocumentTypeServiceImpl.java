package com.adac.portail.service;

import com.adac.portail.dto.request.DocumentTypeRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.dto.response.DocumentTypeResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.DocumentType;
import com.adac.portail.entity.enums.DeadlineAnchor;
import com.adac.portail.exception.BadRequestException;
import com.adac.portail.exception.DocumentTypeAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.DocumentTypeMapper;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentTypeServiceImpl implements DocumentTypeService {

    private static final String NAME_TAKEN_MESSAGE = "Ce type de document existe déjà";
    private static final String NOT_FOUND_MESSAGE = "Type de document introuvable";
    private static final String CATEGORY_NOT_FOUND_MESSAGE = "Catégorie introuvable";
    private static final int MAX_DEADLINE_DAYS = 365;
    private static final String NAME_INDEX = "uk_document_types_nom_upper";

    /** Mêmes formats que le dépôt de documents (tech.md § 6) ; un type peut en autoriser un sous-ensemble. */
    private static final Set<String> KNOWN_FORMATS = Set.of("pdf", "jpg", "jpeg", "png", "docx", "xlsx");

    private static final Pattern ACCENTS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final DocumentTypeRepository documentTypeRepository;
    private final CategoryRepository categoryRepository;
    private final DocumentTypeMapper documentTypeMapper;

    @Override
    @Transactional
    public DocumentTypeResponse createDocumentType(DocumentTypeRequest request) {
        String nom = request.getNom().trim();
        if (documentTypeRepository.existsByNomIgnoreCase(nom)) {
            throw new DocumentTypeAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
        DocumentType documentType = new DocumentType();
        documentType.setActive(true);
        apply(documentType, nom, request);
        return saveOrThrowIfNameTaken(documentType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> getDocumentTypes(Boolean active) {
        List<DocumentType> types = Boolean.TRUE.equals(active)
                ? documentTypeRepository.findAllByActiveTrue()
                : documentTypeRepository.findAll();
        return types.stream()
                .sorted((a, b) -> sortKey(a.getNom()).compareTo(sortKey(b.getNom())))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DocumentTypeResponse updateDocumentType(Long id, DocumentTypeRequest request) {
        DocumentType documentType = findOrThrow(id);
        String nom = request.getNom().trim();
        if (documentTypeRepository.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new DocumentTypeAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
        apply(documentType, nom, request);
        return saveOrThrowIfNameTaken(documentType);
    }

    @Override
    @Transactional
    public DocumentTypeResponse activateDocumentType(Long id) {
        return setActive(id, true);
    }

    @Override
    @Transactional
    public DocumentTypeResponse deactivateDocumentType(Long id) {
        return setActive(id, false);
    }

    private DocumentTypeResponse setActive(Long id, boolean active) {
        DocumentType documentType = findOrThrow(id);
        documentType.setActive(active);
        return toResponse(documentTypeRepository.save(documentType));
    }

    // Les catégories d'un type forment un ensemble sans ordre : la réponse les range par nom pour rester stable.
    private DocumentTypeResponse toResponse(DocumentType documentType) {
        DocumentTypeResponse response = documentTypeMapper.toResponse(documentType);
        if (response.getCategories() != null) {
            response.setCategories(response.getCategories().stream()
                    .sorted(Comparator.comparing(CategoryResponse::getNom, String.CASE_INSENSITIVE_ORDER))
                    .toList());
        }
        return response;
    }

    private DocumentType findOrThrow(Long id) {
        return documentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    // Copie la requête (déjà validée champ par champ) dans le type après les contrôles qui croisent plusieurs champs.
    // Le PUT remplace tout : un champ absent efface l'ancienne valeur. L'état actif n'est jamais touché ici.
    private void apply(DocumentType documentType, String nom, DocumentTypeRequest request) {
        Set<Category> categories = resolveCategories(request);
        validateDeadline(request.getDeadlineDays(), request.getDeadlineAnchor());
        documentType.setNom(nom);
        documentType.setAide(blankToNull(request.getAide()));
        documentType.setSensitive(request.getSensitive());
        documentType.setAppliesToAll(request.getAppliesToAllFormations());
        documentType.setCategories(categories);
        documentType.setDeadlineDays(request.getDeadlineDays());
        documentType.setDeadlineAnchor(request.getDeadlineAnchor());
        documentType.setAllowedFormats(normalizeFormats(request.getAllowedFormats()));
    }

    private Set<Category> resolveCategories(DocumentTypeRequest request) {
        Set<Long> ids = request.getCategoryIds() == null ? Set.of() : new HashSet<>(request.getCategoryIds());
        if (request.getAppliesToAllFormations()) {
            if (!ids.isEmpty()) {
                throw new BadRequestException(
                        "Un type pour toutes les formations ne peut pas être lié à des catégories");
            }
            return new HashSet<>();
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("Choisissez au moins une catégorie, ou « toutes les formations »");
        }
        List<Category> found = categoryRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException(CATEGORY_NOT_FOUND_MESSAGE);
        }
        return new HashSet<>(found);
    }

    private void validateDeadline(Integer days, DeadlineAnchor anchor) {
        if ((days == null) != (anchor == null)) {
            throw new BadRequestException("Le délai demande un nombre de jours et un repère (avant le début ou après la fin)");
        }
        if (days != null && (days < 0 || days > MAX_DEADLINE_DAYS)) {
            throw new BadRequestException("Le délai doit être compris entre 0 et " + MAX_DEADLINE_DAYS + " jours");
        }
    }

    private String normalizeFormats(List<String> formats) {
        if (formats == null || formats.isEmpty()) {
            return null;
        }
        List<String> normalized = formats.stream()
                .map(format -> format.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String format : normalized) {
            if (!KNOWN_FORMATS.contains(format)) {
                throw new BadRequestException("Format non autorisé : " + format);
            }
        }
        return normalized.stream().collect(Collectors.joining(","));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // Tri par nom sans casse ni accents (« Élève » entre « Attestation » et « zèbre »).
    private static String sortKey(String nom) {
        return ACCENTS.matcher(Normalizer.normalize(nom, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean violatesNameIndex(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        return message != null && message.contains(NAME_INDEX);
    }

    private DocumentTypeResponse saveOrThrowIfNameTaken(DocumentType documentType) {
        try {
            return toResponse(documentTypeRepository.saveAndFlush(documentType));
        } catch (DataIntegrityViolationException ex) {
            // Seul l'index d'unicité du nom veut dire « nom déjà pris » ; une autre contrainte (clé étrangère d'une
            // catégorie supprimée entre-temps, CHECK) est une vraie erreur qu'un 409 trompeur cacherait.
            if (violatesNameIndex(ex)) {
                throw new DocumentTypeAlreadyExistsException(NAME_TAKEN_MESSAGE);
            }
            throw ex;
        }
    }
}
