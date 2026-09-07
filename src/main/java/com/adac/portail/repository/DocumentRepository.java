package com.adac.portail.repository;

import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * {@code @EntityGraph} fetch-joins {@code uploadedBy} (branch-wide review) — without it,
     * {@code DocumentMapper.toResponse} touching each row's {@code uploadedBy} (a LAZY proxy) is
     * an N+1, one query per distinct uploader — the same anti-pattern
     * {@code InscriptionRepository.findAllByFormation} was already reviewed and fixed for.
     */
    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findAllByFormation(Formation formation);

    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findAllByInscription(Inscription inscription);
}
