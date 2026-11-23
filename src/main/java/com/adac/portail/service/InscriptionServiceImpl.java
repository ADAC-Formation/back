package com.adac.portail.service;

import com.adac.portail.dto.request.InscriptionRequest;
import com.adac.portail.dto.response.InscriptionResponse;
import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateInscriptionException;
import com.adac.portail.exception.FormationArchivedException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.InscriptionMapper;
import com.adac.portail.repository.DocumentRepository;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InscriptionServiceImpl implements InscriptionService {

    private final InscriptionRepository inscriptionRepository;
    private final FormationRepository formationRepository;
    private final UserRepository userRepository;
    private final InscriptionMapper inscriptionMapper;
    private final FormationService formationService;
    private final ActivationService activationService;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public List<InscriptionResponse> getInscriptions(Long formationId, AdacUserDetails principal) {

        Formation formation = formationService.findVisibleFormationOrThrow(formationId, principal);
        List<Inscription> inscriptions = inscriptionRepository.findAllByFormation(formation);

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

        int inscriptionsCount = (int) inscriptionRepository.countByFormation(formation);
        return inscriptionMapper.toResponse(saved, inscriptionsCount);
    }

    @Override
    @Transactional
    public void deleteInscription(Long formationId, Long stagiaireId) {
        Formation formation = findFormationOrThrow(formationId);
        if (formation.getStatus() == FormationStatus.ARCHIVED) {
            throw new ConflictException("Formation archivée, désinscription impossible");
        }
        inscriptionRepository.findByStagiaire_IdAndFormation_Id(stagiaireId, formationId)
                .ifPresent(this::deleteWithItsDocuments);
    }

    private void deleteWithItsDocuments(Inscription inscription) {
        List<Document> documents = documentRepository.findAllByInscription(inscription);
        List<String> storagePaths = documents.stream()
                .map(Document::getStoragePath)
                .filter(path -> !path.isBlank())
                .toList();
        documentRepository.deleteAll(documents);
        inscriptionRepository.delete(inscription);
        inscriptionRepository.flush();
        storagePaths.forEach(this::deleteStoredFile);
    }

    private void deleteStoredFile(String path) {
        try {
            storageService.delete(path);
        } catch (RuntimeException e) {
            log.error("Failed to delete storage object {} for an unenrolled inscription", path, e);
        }
    }

    private Formation findFormationOrThrow(Long id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable"));
    }

    private User findStagiaireOrThrow(Long stagiaireId) {
        User user = userRepository.findById(stagiaireId)
                .orElseThrow(() -> new ResourceNotFoundException("Stagiaire introuvable"));
        if (user.getRole() != Role.STAGIAIRE || isSuspended(user)) {
            throw new ResourceNotFoundException("Stagiaire introuvable");
        }
        return user;
    }

    private boolean isSuspended(User user) {
        return !user.isActive() && activationService.hasEverActivated(user);
    }
}
