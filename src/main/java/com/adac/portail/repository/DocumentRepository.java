package com.adac.portail.repository;

import com.adac.portail.entity.Document;
import com.adac.portail.entity.Formation;
import com.adac.portail.entity.Inscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findAllByFormation(Formation formation);

    @EntityGraph(attributePaths = "uploadedBy")
    List<Document> findAllByInscription(Inscription inscription);
}
