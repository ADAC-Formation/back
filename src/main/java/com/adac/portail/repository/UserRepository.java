package com.adac.portail.repository;

import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Used by {@code ExcelImportUtil} (TICKET-023 review) — the "formateur" column is a human-
     * typed email, and {@link #findByEmail} being case-sensitive rejected an otherwise-valid
     * account for a capitalization difference (inconsistent with the category-name lookup right
     * next to it, which is already case-insensitive).
     */
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByRole(Role role);

    /** Used by {@code UserServiceImpl.deactivate} to refuse suspending the last active SUPER_ADMIN. */
    long countByRoleAndIsActiveTrue(Role role);
}
