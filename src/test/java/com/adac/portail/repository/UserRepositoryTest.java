package com.adac.portail.repository;

import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savedUserHasEmailNotificationsEnabledByDefault() {
        User user = User.builder()
                .email("user-repo-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build();

        User saved = userRepository.save(user);

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isEmailNotificationsEnabled()).isTrue();
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void findByEmailReturnsUserWhenEmailIsKnown() {
        userRepository.save(User.builder()
                .email("find-by-email-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.STAGIAIRE)
                .build());

        Optional<User> found = userRepository.findByEmail("find-by-email-test@adac.fr");

        assertThat(found).isPresent();
        assertThat(found.get().getNom()).isEqualTo("Doe");
    }

    @Test
    void findByEmailReturnsEmptyWhenEmailIsUnknown() {
        Optional<User> found = userRepository.findByEmail("unknown@adac.fr");

        assertThat(found).isEmpty();
    }

    // TICKET-023 — used by ExcelImportUtil to resolve the "formateur" column.
    @Test
    void findByEmailIgnoreCaseMatchesRegardlessOfCase() {
        userRepository.save(User.builder()
                .email("find-by-email-ignorecase-test@adac.fr")
                .passwordHash("hashed")
                .nom("Doe")
                .prenom("Jane")
                .role(Role.ADMIN)
                .build());

        Optional<User> found = userRepository.findByEmailIgnoreCase("Find-By-Email-IgnoreCase-Test@ADAC.fr");

        assertThat(found).isPresent();
        assertThat(found.get().getNom()).isEqualTo("Doe");
    }
}
