package com.adac.portail.service;

import com.adac.portail.dto.request.CreateFormationRequest;
import com.adac.portail.dto.request.UpdateFormationRequest;
import com.adac.portail.dto.response.FormationResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.InvalidFormationDataException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.FormationMapper;
import com.adac.portail.repository.CategoryRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** See {@link FormationService} for the contract; docs/tech.md § 4 for the wire shapes. */
@Service
@RequiredArgsConstructor
public class FormationServiceImpl implements FormationService {

    private static final String NOT_FOUND_MESSAGE = "Formation introuvable";

    private final FormationRepository formationRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final InscriptionRepository inscriptionRepository;
    private final FormationMapper formationMapper;

    @Override
    @Transactional
    public FormationResponse createFormation(CreateFormationRequest request, AdacUserDetails principal) {
        Category category = findCategoryOrThrow(request.getCategoryId());
        User caller = principal.getUser();
        // request.getFormateurId() null -> auto-assign the calling Super Admin (docs/tech.md).
        User formateur = request.getFormateurId() != null
                ? findFormateurOrThrow(request.getFormateurId())
                : caller;

        Formation formation = Formation.builder()
                .intitule(request.getIntitule())
                .description(request.getDescription())
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .modalite(request.getModalite())
                .category(category)
                .formateur(formateur)
                .createdBy(caller)
                // status defaults to ACTIVE via Formation.status's @Builder.Default.
                .build();

        return toResponse(formationRepository.save(formation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormationResponse> getFormations(FormationStatus status, Long categoryId, AdacUserDetails principal) {
        User caller = principal.getUser();
        List<Formation> base = switch (caller.getRole()) {
            case SUPER_ADMIN -> formationRepository.findAll();
            // docs/tech.md, "filtre par défaut : ses formations" — same convention as
            // UserServiceImpl.getStagiaires for an ADMIN caller.
            case ADMIN -> formationRepository.findAllByFormateur(caller);
            case STAGIAIRE -> inscriptionRepository.findFormationsByStagiaire(caller);
        };

        return base.stream()
                .filter(formation -> status == null || formation.getStatus() == status)
                .filter(formation -> categoryId == null || Objects.equals(formation.getCategory().getId(), categoryId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FormationResponse getFormationById(Long id, AdacUserDetails principal) {
        Formation formation = findFormationOrThrow(id);
        User caller = principal.getUser();
        // Scoped like GET /api/formations, not left wide open (review: this endpoint used to be
        // the one door UserServiceImpl.getById/getStagiaires' ADMIN scoping didn't cover, letting
        // any ADMIN enumerate every formation — including the Super Admin's and other formateurs'
        // — by id). 404, not 403, for the ADMIN case: same reasoning as UserServiceImpl.getById,
        // a status that doesn't confirm the formation exists to someone who isn't allowed to see
        // it. STAGIAIRE keeps the 403 docs/tech.md documents ("STAGIAIRE non inscrit").
        if (caller.getRole() == Role.ADMIN && !isOwnFormation(formation, caller)) {
            throw new ResourceNotFoundException(NOT_FOUND_MESSAGE);
        }
        if (caller.getRole() == Role.STAGIAIRE && !inscriptionRepository.existsByStagiaireAndFormation(caller, formation)) {
            throw new AccessDeniedException("Accès refusé");
        }
        return toResponse(formation);
    }

    @Override
    @Transactional
    public FormationResponse updateFormation(Long id, UpdateFormationRequest request) {
        Formation formation = findFormationOrThrow(id);
        if (formation.getStatus() == FormationStatus.ARCHIVED) {
            throw new FormationArchivedException("Formation archivée, modification impossible");
        }

        // Resolve every referenced/merged value *before* mutating the managed entity (review):
        // findCategoryOrThrow/findFormateurOrThrow can throw, and the date-order check needs the
        // merged dateDebut/dateFin either way — validating first means a rejected request never
        // leaves the entity partially mutated.
        Category category = request.getCategoryId() != null ? findCategoryOrThrow(request.getCategoryId()) : null;
        User formateur = request.getFormateurId() != null ? findFormateurOrThrow(request.getFormateurId()) : null;
        LocalDate dateDebut = request.getDateDebut() != null ? request.getDateDebut() : formation.getDateDebut();
        LocalDate dateFin = request.getDateFin() != null ? request.getDateFin() : formation.getDateFin();
        if (dateFin.isBefore(dateDebut)) {
            throw new InvalidFormationDataException("dateFin must be on or after dateDebut");
        }

        // Only the fields present (non-null) on request are applied — see
        // UpdateFormationRequest's Javadoc.
        if (request.getIntitule() != null) {
            formation.setIntitule(request.getIntitule());
        }
        if (request.getDescription() != null) {
            formation.setDescription(request.getDescription());
        }
        formation.setDateDebut(dateDebut);
        formation.setDateFin(dateFin);
        if (request.getModalite() != null) {
            formation.setModalite(request.getModalite());
        }
        if (category != null) {
            formation.setCategory(category);
        }
        if (formateur != null) {
            formation.setFormateur(formateur);
        }

        return toResponse(formationRepository.save(formation));
    }

    @Override
    @Transactional
    public FormationResponse archiveFormation(Long id) {
        Formation formation = findFormationOrThrow(id);
        // Idempotent — same convention as CategoryServiceImpl.activateCategory/deactivateCategory.
        formation.setStatus(FormationStatus.ARCHIVED);
        return toResponse(formationRepository.save(formation));
    }

    private FormationResponse toResponse(Formation formation) {
        long inscriptionsCount = inscriptionRepository.countByFormation(formation);
        return formationMapper.toResponse(formation, (int) inscriptionsCount);
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidFormationDataException("categoryId introuvable"));
    }

    /**
     * Rejects a {@code STAGIAIRE} id and a deactivated account (review) — "formateur" means an
     * active {@code ADMIN} or {@code SUPER_ADMIN} everywhere else in this codebase (see
     * {@code UserServiceImpl.createFormateur}), and every formateur-scoped query
     * ({@code FormationRepository.findAllByFormateur}, {@code getFormations} above) trusts the FK
     * without re-checking the role.
     */
    private User findFormateurOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Formateur introuvable"));
        if (user.getRole() == Role.STAGIAIRE || !user.isActive()) {
            throw new InvalidFormationDataException("formateurId invalide : doit être un formateur actif");
        }
        return user;
    }

    private Formation findFormationOrThrow(Long id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /**
     * {@code formation.getFormateur()} is a LAZY proxy, but its id is set at proxy-creation time
     * and reading it never triggers initialization — safe to compare without a database round
     * trip (same reasoning as {@code UserServiceImpl.isOwnFormation}). {@code null} means the
     * Super Admin auto-assigned themselves, which never matches an ADMIN caller.
     */
    private boolean isOwnFormation(Formation formation, User formateur) {
        return formation.getFormateur() != null && formation.getFormateur().getId().equals(formateur.getId());
    }
}
