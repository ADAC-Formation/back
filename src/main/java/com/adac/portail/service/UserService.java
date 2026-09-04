package com.adac.portail.service;

import com.adac.portail.dto.request.CreateUserRequest;
import com.adac.portail.dto.request.UpdateProfileRequest;
import com.adac.portail.dto.response.UserResponse;
import com.adac.portail.exception.DuplicateEmailException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.security.AdacUserDetails;

import java.util.List;

/**
 * Formateur/stagiaire account management (US-007, US-008) — see docs/tech.md § 2. Handled here
 * rather than a generic "user" concept: the two roles have different creation payloads
 * (stagiaires enroll in formations up front) and different list-visibility rules per caller role.
 */
public interface UserService {

    /**
     * @throws DuplicateEmailException {@code request.getEmail()} already belongs to another user
     */
    UserResponse createFormateur(CreateUserRequest request);

    /**
     * Enrolls the new stagiaire in every formation listed in {@code request.getFormationIds()}
     * (ignored/empty is allowed — see CreateUserRequest's Javadoc).
     *
     * @throws DuplicateEmailException  {@code request.getEmail()} already belongs to another user
     * @throws ResourceNotFoundException one of {@code request.getFormationIds()} doesn't exist
     */
    UserResponse createStagiaire(CreateUserRequest request);

    /**
     * @param active    optional filter, ignored (forced to {@code true}) when {@code principal}
     *                  is an ADMIN — see docs/tech.md, "ADMIN : actifs uniquement"
     * @param principal the caller, whose role decides the visibility rule above
     */
    List<UserResponse> getFormateurs(Boolean active, AdacUserDetails principal);

    /**
     * @param active      optional filter, ignored (forced to {@code true}) when {@code principal}
     *                    is an ADMIN — see docs/tech.md, "ADMIN : actifs uniquement"
     * @param formationId optional — restrict to stagiaires enrolled in this formation
     * @param principal   the caller; an ADMIN only ever sees stagiaires enrolled in a formation
     *                    they teach (docs/tech.md, "filtre par défaut : ses formations")
     */
    List<UserResponse> getStagiaires(Boolean active, Long formationId, AdacUserDetails principal);

    /**
     * @throws ResourceNotFoundException no user with this id, or (TICKET-019 review) the caller
     *                                    is an ADMIN and the target is a STAGIAIRE not enrolled in
     *                                    any formation they teach — same status as an unknown id,
     *                                    on purpose: a 403 here would confirm the id exists.
     */
    UserResponse getById(Long id, AdacUserDetails principal);

    /**
     * @throws ResourceNotFoundException no user with this id
     * @throws com.adac.portail.exception.ConflictException (TICKET-019 review) {@code id} is the
     *                                    caller's own account, or the target is the last active
     *                                    SUPER_ADMIN — both would lock the portal's only admin out
     *                                    with no recovery path (see docs/ARCHI.md — Authentification,
     *                                    no server-side session revocation exists either way)
     */
    UserResponse deactivate(Long id, AdacUserDetails principal);

    /**
     * @throws ResourceNotFoundException no user with this id
     * @throws com.adac.portail.exception.ConflictException (TICKET-019 review) the target has
     *                                    never completed its first activation — {@code isActive}
     *                                    doubles as "pending" and "suspended" (see
     *                                    {@code ActivationServiceImpl.isPendingFirstActivation}),
     *                                    and flipping a pending account active here would make it
     *                                    permanently ineligible for {@code /activate}
     */
    UserResponse reactivate(Long id);

    /**
     * Partial update of the caller's own profile (any authenticated role) — see docs/tech.md,
     * "PATCH /api/users/me". Only {@code emailNotificationsEnabled} is settable for now; a
     * {@code null} field is left unchanged.
     */
    UserResponse updateMe(AdacUserDetails principal, UpdateProfileRequest request);
}
