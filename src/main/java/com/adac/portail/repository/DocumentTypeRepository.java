package com.adac.portail.repository;

import com.adac.portail.entity.Category;
import com.adac.portail.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    List<DocumentType> findAllByActiveTrue();

    /** Types actifs à envoyer pour une formation de cette catégorie : ceux de toutes les formations et ceux liés à elle. */
    @Query("""
            select distinct t from DocumentType t left join t.categories c
            where t.active = true and (t.appliesToAll = true or c = :category)
            """)
    List<DocumentType> findActiveApplicableTo(@Param("category") Category category);
}
