package com.adac.portail.service;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.request.UpdateUserRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.RateLimitException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final FormationRepository formationRepository;
    private final InscriptionRepository inscriptionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ActivationService activationService;

    @Override
    @Transactional
    public UserResponse createFormateur(CreateUserRequest request) {
        User user = createPendingUser(request, Role.ADMIN);
        sendActivationCodeAfterCommit(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createStagiaire(CreateUserRequest request) {
        User user = createPendingUser(request, Role.STAGIAIRE);
        enroll(user, request.getFormationIds());
        sendActivationCodeAfterCommit(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getFormateurs(Boolean active, AdacUserDetails principal) {
        boolean forceActiveOnly = principal.getUser().getRole() == Role.ADMIN;
        return userRepository.findAllByRole(Role.ADMIN).stream()
                .filter(formateur -> matchesActiveFilter(formateur, active, forceActiveOnly))
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getStagiaires(Boolean active, Long formationId, AdacUserDetails principal) {
        boolean isAdmin = principal.getUser().getRole() == Role.ADMIN;

        List<User> stagiaires;
        if (formationId != null) {
            Formation formation = formationRepository.findById(formationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable"));

            if (isAdmin && !isOwnFormation(formation, principal.getUser())) {
                throw new ResourceNotFoundException("Formation introuvable");
            }
            stagiaires = inscriptionRepository.findStagiairesByFormation(formation);
        } else if (isAdmin) {

            stagiaires = inscriptionRepository.findStagiairesByFormateur(principal.getUser());
        } else {
            stagiaires = userRepository.findAllByRole(Role.STAGIAIRE);
        }

        return stagiaires.stream()
                .filter(stagiaire -> matchesActiveFilter(stagiaire, active, isAdmin))
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id, AdacUserDetails principal) {
        User target = findUserOrThrow(id);
        User caller = principal.getUser();

        if (caller.getRole() == Role.ADMIN && !Objects.equals(target.getId(), caller.getId())) {
            boolean visible = switch (target.getRole()) {
                case STAGIAIRE -> target.isActive()
                        && inscriptionRepository.existsByStagiaireAndFormation_Formateur(target, caller);
                case ADMIN -> target.isActive();
                case SUPER_ADMIN -> false;
            };
            if (!visible) {
                throw new ResourceNotFoundException("Utilisateur introuvable");
            }
        }
        return userMapper.toResponse(target);
    }

    private boolean matchesActiveFilter(User user, Boolean active, boolean forceActiveOnly) {
        if (forceActiveOnly) {
            return user.isActive();
        }
        return active == null || user.isActive() == active;
    }

    @Override
    @Transactional
    public UserResponse deactivate(Long id, AdacUserDetails principal) {
        User user = findUserOrThrow(id);

        if (Objects.equals(user.getId(), principal.getUser().getId())) {
            throw new ConflictException("Vous ne pouvez pas suspendre votre propre compte");
        }
        if (user.getRole() == Role.SUPER_ADMIN && userRepository.countByRoleAndIsActiveTrue(Role.SUPER_ADMIN) <= 1) {
            throw new ConflictException("Impossible de suspendre le dernier compte Super Admin actif");
        }
        user.setActive(false);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse reactivate(Long id) {
        User user = findUserOrThrow(id);
        if (!activationService.hasEverActivated(user)) {

            throw new ConflictException(
                    "Ce compte n'a jamais été activé — utilisez le renvoi du code d'activation, pas la réactivation");
        }
        user.setActive(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateMe(AdacUserDetails principal, UpdateProfileRequest request) {

        User user = findUserOrThrow(principal.getUser().getId());
        if (request.getEmailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request, AdacUserDetails principal) {
        User user = findUserOrThrow(id);

        if (request.getNom() != null) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null) {
            user.setPrenom(request.getPrenom());
        }

        boolean emailChanged = false;

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {

            userRepository.findByEmailIgnoreCase(request.getEmail())
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new DuplicateEmailException("Cet email est déjà utilisé");
                    });
            user.setEmail(request.getEmail());
            emailChanged = true;
        }

        User saved = userRepository.save(user);

        log.info("User id={} edited by admin id={} (nomChanged={}, prenomChanged={}, emailChanged={})",
                saved.getId(), principal.getUser().getId(),
                request.getNom() != null, request.getPrenom() != null, emailChanged);

        if (emailChanged && !activationService.hasEverActivated(saved)) {
            sendActivationCodeAfterCommit(saved);
        }

        return userMapper.toResponse(saved);
    }

    private User createPendingUser(CreateUserRequest request, Role role) {
        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new DuplicateEmailException("Cet email est déjà utilisé");
        });

        User user = User.builder()
                .email(request.getEmail())
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .role(role)
                .isActive(false)

                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .build();
        return userRepository.save(user);
    }

    private void enroll(User stagiaire, List<Long> formationIds) {
        if (formationIds == null || formationIds.isEmpty()) {
            return;
        }

        List<Long> distinctIds = formationIds.stream().distinct().toList();
        List<Formation> formations = formationRepository.findAllById(distinctIds);
        if (formations.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("Une ou plusieurs formations sont introuvables");
        }
        for (Formation formation : formations) {
            inscriptionRepository.save(Inscription.builder()
                    .stagiaire(stagiaire)
                    .formation(formation)
                    .build());
        }
    }

    private boolean isOwnFormation(Formation formation, User formateur) {

        return formation.getFormateur() != null && formation.getFormateur().getId().equals(formateur.getId());
    }

    private void sendActivationCodeAfterCommit(User user) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendActivationCodeIgnoringRateLimit(user);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendActivationCodeIgnoringRateLimit(user);
            }
        });
    }

    private void sendActivationCodeIgnoringRateLimit(User user) {
        try {
            activationService.sendActivationCode(user);
        } catch (RateLimitException e) {
            log.info("Activation code resend rate-limited for user id={}, data change already applied", user.getId());
        }
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
