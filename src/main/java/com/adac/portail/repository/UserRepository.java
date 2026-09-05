package com.adac.portail.repository;

import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findAllByRole(Role role);

    /** Used by {@code UserServiceImpl.deactivate} to refuse suspending the last active SUPER_ADMIN. */
    long countByRoleAndIsActiveTrue(Role role);
}
