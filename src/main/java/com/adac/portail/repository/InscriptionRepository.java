package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    List<Inscription> findAllByFormation(Formation formation);

    /** Named after the entity's actual field ({@code stagiaire}, not {@code user}). */
    List<Inscription> findAllByStagiaire(User stagiaire);

    /**
     * Projects straight to the enrolled {@code User}s instead of {@code Inscription} — used by
     * {@code UserServiceImpl.getStagiaires} (TICKET-019 review fix). Going through
     * {@code findAllByFormation(...).stream().map(Inscription::getStagiaire)} instead would hand
     * back an uninitialized LAZY proxy per {@code stagiaire} (see {@code Inscription.stagiaire}) —
     * with {@code spring.jpa.open-in-view: false} and no open transaction at that call site, every
     * touch of it (even {@code isActive()} in the caller's filter) threw
     * {@code LazyInitializationException}. This query lets Hibernate do the join once, in SQL.
     */
    @Query("select i.stagiaire from Inscription i where i.formation = :formation")
    List<User> findStagiairesByFormation(@Param("formation") Formation formation);

    /**
     * All distinct stagiaires enrolled in any formation this formateur teaches — used by
     * {@code UserServiceImpl.getStagiaires} for an ADMIN caller. {@code distinct} runs in SQL
     * rather than via {@code Stream.distinct()} in the service: {@code User} has no
     * {@code equals}/{@code hashCode} override, so a Java-side dedupe would silently depend on
     * JPA persistence-context identity instead (see TICKET-019 review).
     */
    @Query("select distinct i.stagiaire from Inscription i where i.formation.formateur = :formateur")
    List<User> findStagiairesByFormateur(@Param("formateur") User formateur);

    /**
     * Is {@code stagiaire} enrolled in a formation taught by {@code formateur}? Used by
     * {@code UserServiceImpl.getById} to scope an ADMIN caller's view of a stagiaire profile to
     * their own formations, the same rule already applied to the list endpoint (TICKET-019 review).
     */
    boolean existsByStagiaireAndFormation_Formateur(User stagiaire, User formateur);
}
