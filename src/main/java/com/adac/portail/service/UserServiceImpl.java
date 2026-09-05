package com.adac.portail.service;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import com.adac.portail.exception.ConflictException;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.UserMapper;
import com.adac.portail.repository.FormationRepository;
import com.adac.portail.repository.InscriptionRepository;
import com.adac.portail.repository.UserRepository;
import com.adac.portail.security.AdacUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** See {@link UserService} for the contract; docs/tech.md § 2 for the wire shapes. */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

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
            // Same 404 as an unknown id when an ADMIN targets a formation they don't teach — a
            // 403 here would itself confirm the formation exists (TICKET-019 review: this branch
            // used to run unconditionally, letting any ADMIN read any formation's roster).
            if (isAdmin && !isOwnFormation(formation, principal.getUser())) {
                throw new ResourceNotFoundException("Formation introuvable");
            }
            stagiaires = inscriptionRepository.findStagiairesByFormation(formation);
        } else if (isAdmin) {
            // docs/tech.md, "filtre par défaut : ses formations" — an ADMIN with no formations yet
            // (Formation CRUD lands in TICKET-022) simply sees an empty list here, not an error.
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
        // An ADMIN gets an unrestricted lookup of their own profile, but every other target is
        // scoped exactly like the list endpoints: a fellow formateur only if still active, a
        // stagiaire only if enrolled in one of the caller's own formations (and active), and a
        // SUPER_ADMIN never (branch-wide review: this had no scoping at all outside the stagiaire
        // case, letting any ADMIN enumerate every SUPER_ADMIN or suspended account by id — a gap
        // every list endpoint already closed).
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

    /**
     * @param forceActiveOnly {@code true} short-circuits to "active only" regardless of
     *                        {@code active} (the ADMIN-viewing-formateurs/stagiaires rule);
     *                        otherwise a {@code null} filter means no restriction and a non-null
     *                        one is honoured as-is — including {@code false} (branch-wide review:
     *                        {@code Boolean.TRUE.equals(active)} used to collapse {@code false}
     *                        and "absent" into the same "no filter" behaviour, so a SUPER_ADMIN
     *                        asking for suspended accounts silently got everyone back).
     */
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
        // Neither logout nor a password change revokes the JWT itself (see docs/ARCHI.md —
        // Authentification), so self-suspension wouldn't even take effect until the cookie
        // expires — but it would still lock the SUPER_ADMIN out of every *other* session/browser
        // with no admin left to undo it (TICKET-019 review).
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
            // "Reactivate" is for a suspended (already-activated-once) account — see
            // UserService's Javadoc for why silently flipping isActive here would be worse than
            // rejecting it (TICKET-019 review).
            throw new ConflictException(
                    "Ce compte n'a jamais été activé — utilisez le renvoi du code d'activation, pas la réactivation");
        }
        user.setActive(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateMe(AdacUserDetails principal, UpdateProfileRequest request) {
        // Re-fetch by id rather than mutating principal.getUser() directly: that instance was
        // loaded by CustomUserDetailsService in the filter's own (already-committed) transaction,
        // so by the time it reaches here it's detached — saving it would merge() a full-row
        // snapshot taken before this request even started, silently reverting any isActive/
        // passwordHash/etc. change made by a concurrent request in between (found in review).
        User user = findUserOrThrow(principal.getUser().getId());
        if (request.getEmailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        return userMapper.toResponse(userRepository.save(user));
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
                // No real password exists yet — the user sets one via POST /api/auth/activate.
                // A random, never-communicated hash keeps `password_hash NOT NULL` satisfied
                // without a guessable/default credential (BCrypt.matches() against it always
                // fails, so login stays impossible until activation regardless).
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .build();
        return userRepository.save(user);
    }

    private void enroll(User stagiaire, List<Long> formationIds) {
        if (formationIds == null || formationIds.isEmpty()) {
            return;
        }
        // distinct() before the lookup: a repeated id in the payload (e.g. a doubled-up form
        // submission) must not hit inscriptions' unique(stagiaire_id, formation_id) constraint
        // and surface as a raw 409 (TICKET-019 review) — enrolling once is the sensible reading
        // of "enroll in these formations" either way.
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
        // formation.getFormateur() is a LAZY proxy, but its id is set at proxy-creation time and
        // reading it never triggers initialization — safe to compare without a database round
        // trip even outside a write transaction. null means the Super Admin auto-assigned
        // themselves (see Formation.formateur's Javadoc), so it never matches an ADMIN caller.
        return formation.getFormateur() != null && formation.getFormateur().getId().equals(formateur.getId());
    }

    /**
     * Defers the activation email until the enclosing transaction actually commits. Both
     * {@code sendActivationCode} and its caller ({@code createFormateur}/{@code createStagiaire})
     * are {@code @Transactional}, so calling it inline would send the mail *before* commit — on
     * the exact concurrent-duplicate-email race {@link com.adac.portail.exception.GlobalExceptionHandler}
     * exists to catch, the loser has already mailed a real activation code for a row that then
     * rolls back (branch-wide review). Falls back to sending immediately when no transaction
     * synchronization is active (e.g. a plain unit test calling the service directly, with no real
     * transaction to hook into) so existing behaviour there is unchanged.
     */
    private void sendActivationCodeAfterCommit(User user) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            activationService.sendActivationCode(user);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                activationService.sendActivationCode(user);
            }
        });
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
