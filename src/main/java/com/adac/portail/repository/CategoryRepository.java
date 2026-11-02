package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    List<Category> findAllByIsActiveTrue();

    Optional<Category> findByNomIgnoreCase(String nom);
}
