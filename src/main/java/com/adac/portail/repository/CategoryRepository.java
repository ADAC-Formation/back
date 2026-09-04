package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNomIgnoreCase(String nom);

    List<Category> findAllByIsActiveTrue();
}
