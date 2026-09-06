package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNomIgnoreCase(String nom);

    /** Used by the PUT /{id} uniqueness check — excludes the category being edited itself. */
    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    List<Category> findAllByIsActiveTrue();

    /** Used by {@code ExcelImportUtil} (TICKET-023) to resolve the "categorie" column by name. */
    Optional<Category> findByNomIgnoreCase(String nom);
}
