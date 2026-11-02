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
import com.adac.portail.utils.ExcelImportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FormationServiceImpl implements FormationService {

    private static final String NOT_FOUND_MESSAGE = "Formation introuvable";

    private final FormationRepository formationRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final InscriptionRepository inscriptionRepository;
    private final FormationMapper formationMapper;
    private final ExcelImportUtil excelImportUtil;

    @Override
    @Transactional
    public FormationResponse createFormation(CreateFormationRequest request, AdacUserDetails principal) {
        Category category = findCategoryOrThrow(request.getCategoryId());
        User caller = principal.getUser();

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

                .build();

        return toResponse(formationRepository.save(formation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormationResponse> getFormations(FormationStatus status, Long categoryId, AdacUserDetails principal) {
        User caller = principal.getUser();
        List<Formation> base = switch (caller.getRole()) {
            case SUPER_ADMIN -> formationRepository.findAll();

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
        return toResponse(findVisibleFormationOrThrow(id, principal));
    }

    @Override
    @Transactional(readOnly = true)
    public Formation findVisibleFormationOrThrow(Long id, AdacUserDetails principal) {
        Formation formation = findFormationOrThrow(id);
        User caller = principal.getUser();

        if (caller.getRole() == Role.ADMIN && !isOwnFormation(formation, caller)) {
            throw new ResourceNotFoundException(NOT_FOUND_MESSAGE);
        }
        if (caller.getRole() == Role.STAGIAIRE && !inscriptionRepository.existsByStagiaireAndFormation(caller, formation)) {
            throw new AccessDeniedException("Accès refusé");
        }
        return formation;
    }

    @Override
    @Transactional
    public FormationResponse updateFormation(Long id, UpdateFormationRequest request) {
        Formation formation = findFormationOrThrow(id);
        if (formation.getStatus() == FormationStatus.ARCHIVED) {
            throw new FormationArchivedException("Formation archivée, modification impossible");
        }

        Category category = request.getCategoryId() != null ? findCategoryOrThrow(request.getCategoryId()) : null;
        User formateur = request.getFormateurId() != null ? findFormateurOrThrow(request.getFormateurId()) : null;
        LocalDate dateDebut = request.getDateDebut() != null ? request.getDateDebut() : formation.getDateDebut();
        LocalDate dateFin = request.getDateFin() != null ? request.getDateFin() : formation.getDateFin();
        if (dateFin.isBefore(dateDebut)) {
            throw new InvalidFormationDataException("dateFin must be on or after dateDebut");
        }

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

        formation.setStatus(FormationStatus.ARCHIVED);
        return toResponse(formationRepository.save(formation));
    }

    @Override
    @Transactional
    public List<FormationResponse> importFormations(MultipartFile file, AdacUserDetails principal) {

        return excelImportUtil.parse(file).stream()
                .map(request -> createFormation(request, principal))
                .toList();
    }

    private FormationResponse toResponse(Formation formation) {
        long inscriptionsCount = inscriptionRepository.countByFormation(formation);
        return formationMapper.toResponse(formation, (int) inscriptionsCount);
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new InvalidFormationDataException("categoryId introuvable"));
    }

    private User findFormateurOrThrow(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() == Role.STAGIAIRE || !user.isActive()) {
            throw new InvalidFormationDataException("formateurId invalide : doit être un formateur actif");
        }
        return user;
    }

    private Formation findFormationOrThrow(Long id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private boolean isOwnFormation(Formation formation, User formateur) {
        return formation.getFormateur() != null && formation.getFormateur().getId().equals(formateur.getId());
    }
}
