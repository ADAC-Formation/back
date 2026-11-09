package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    @EntityGraph(attributePaths = "stagiaire")
    List<Inscription> findAllByFormation(Formation formation);

    long countByFormation(Formation formation);

    List<Inscription> findAllByStagiaire(User stagiaire);

    @Query("select i.stagiaire from Inscription i where i.formation = :formation")
    List<User> findStagiairesByFormation(@Param("formation") Formation formation);

    @Query("select distinct i.stagiaire from Inscription i where i.formation.formateur = :formateur")
    List<User> findStagiairesByFormateur(@Param("formateur") User formateur);

    boolean existsByStagiaireAndFormation_Formateur(User stagiaire, User formateur);

    Optional<Inscription> findByStagiaireAndFormation(User stagiaire, Formation formation);

    boolean existsByStagiaireAndFormation(User stagiaire, Formation formation);

    @Query("select i.formation from Inscription i where i.stagiaire = :stagiaire")
    List<Formation> findFormationsByStagiaire(@Param("stagiaire") User stagiaire);

    void deleteByStagiaire_IdAndFormation_Id(Long stagiaireId, Long formationId);

    @Query("""
            select distinct i.stagiaire from Inscription i
            where i.stagiaire not in (
                select d.inscription.stagiaire from Document d where d.inscription is not null
            )
            """)
    List<User> findStagiairesWithNoDocuments();
}
