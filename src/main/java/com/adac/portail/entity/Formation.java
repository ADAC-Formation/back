package com.adac.portail.entity;

import com.adac.portail.entity.enums.FormationStatus;
import com.adac.portail.entity.enums.Modalite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "formations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Formation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String intitule;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modalite modalite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FormationStatus status = FormationStatus.ACTIVE;

    /** NOT NULL since V2 (TICKET-046) — every formation belongs to exactly one category. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Nullable — NULL means the Super Admin is the auto-assigned instructor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formateur_id")
    private User formateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
