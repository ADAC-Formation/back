package com.adac.portail.service;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.DuplicateInscriptionException;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.InscriptionMapper;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** See {@link InscriptionService} for the contract; docs/tech.md § 5 for the wire shapes. */
@Service
@RequiredArgsConstructor
public class InscriptionServiceImpl implements InscriptionService {

    private final InscriptionRepository inscriptionRepository;
    private final FormationRepository formationRepository;
    private final UserRepository userRepository;
    private final InscriptionMapper inscriptionMapper;
    private final FormationService formationService;

    @Override
    @Transactional(readOnly = true)
    public List<InscriptionResponse> getInscriptions(Long formationId, AdacUserDetails principal) {
        // One lookup, one rule (review): findVisibleFormationOrThrow both authorizes (throws
        // ResourceNotFoundException/AccessDeniedException, same as GET /api/formations/{id}) and
        // returns the entity this method actually needs — no second findById, no discarded DTO.
        Formation formation = formationService.findVisibleFormationOrThrow(formationId, principal);
        List<Inscription> inscriptions = inscriptionRepository.findAllByFormation(formation);
        // Every row shares this formation, so its enrollment count is just the list size — no
        // extra query needed (review: this used to leave formation.inscriptionsCount at a
        // hardcoded 0 on every response, contradicting docs/tech.md's "calculé").
        int inscriptionsCount = inscriptions.size();
        return inscriptions.stream()
                .map(inscription -> inscriptionMapper.toResponse(inscription, inscriptionsCount))
                .toList();
    }

    @Override
    @Transactional
    public InscriptionResponse createInscription(Long formationId, InscriptionRequest request) {
        Formation formation = findFormationOrThrow(formationId);
        if (formation.getStatus() == FormationStatus.ARCHIVED) {
            throw new FormationArchivedException("Formation archivée, inscription impossible");
        }
        User stagiaire = findStagiaireOrThrow(request.getStagiaireId());
        if (inscriptionRepository.existsByStagiaireAndFormation(stagiaire, formation)) {
            throw new DuplicateInscriptionException("Stagiaire déjà inscrit à cette formation");
        }

        Inscription inscription = Inscription.builder()
                .stagiaire(stagiaire)
                .formation(formation)
                .build();
        Inscription saved = inscriptionRepository.save(inscription);
        // +1 query, but only on the write path (unlike the list above, there's no free count to
        // reuse here) — review: same "don't hardcode 0" fix as getInscriptions.
        int inscriptionsCount = (int) inscriptionRepository.countByFormation(formation);
        return inscriptionMapper.toResponse(saved, inscriptionsCount);
    }

    @Override
    @Transactional
    public void deleteInscription(Long formationId, Long stagiaireId) {
        // Only the formation needs to exist for a 404 — deleteByStagiaire_IdAndFormation_Id is a
        // no-op derived delete (0 rows) when stagiaireId was never enrolled, which is exactly the
        // idempotent behaviour docs/tech.md's "204 No Content" implies (see InscriptionService's
        // Javadoc) — no separate existence check needed before calling it.
        findFormationOrThrow(formationId);
        inscriptionRepository.deleteByStagiaire_IdAndFormation_Id(stagiaireId, formationId);
    }

    private Formation findFormationOrThrow(Long id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable"));
    }

    /**
     * "Stagiaire" means an active {@code Role.STAGIAIRE} account — same reasoning as
     * {@code FormationServiceImpl.findFormateurOrThrow} rejecting a stagiaire id or a deactivated
     * account for {@code formateurId} (review: this used to check the role but not
     * {@code isActive}, inconsistent with that sibling check). A wrong-role or deactivated id is
     * reported the same as an unknown one (404, not a separate 400) — it doesn't exist as an
     * enrollable stagiaire, which is all the caller asked about.
     */
    private User findStagiaireOrThrow(Long stagiaireId) {
        User user = userRepository.findById(stagiaireId)
                .orElseThrow(() -> new ResourceNotFoundException("Stagiaire introuvable"));
        if (user.getRole() != Role.STAGIAIRE || !user.isActive()) {
            throw new ResourceNotFoundException("Stagiaire introuvable");
        }
        return user;
    }
}
