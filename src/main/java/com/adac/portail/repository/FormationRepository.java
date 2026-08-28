package com.adac.portail.repository;

import com.adac.portail.entity.Formation;
import com.adac.portail.entity.User;
import com.adac.portail.entity.enums.FormationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormationRepository extends JpaRepository<Formation, Long> {

    List<Formation> findByStatus(FormationStatus status);

    List<Formation> findAllByFormateur(User formateur);
}
