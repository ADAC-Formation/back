package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import com.adac.portail.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InscriptionRepository extends JpaRepository<Inscription, Long> {

    List<Inscription> findAllByFormation(Formation formation);

    /** Named after the entity's actual field ({@code stagiaire}, not {@code user}). */
    List<Inscription> findAllByStagiaire(User stagiaire);
}
