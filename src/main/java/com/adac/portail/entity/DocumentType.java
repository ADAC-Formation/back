package com.adac.portail.entity;

import com.adac.portail.entity.enums.DeadlineAnchor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Type de document attendu d'un stagiaire (pièce d'identité, certificat médical…). Soit pour toutes les formations
 * ({@code appliesToAll}), soit pour les formations des catégories liées. Désactivé, jamais supprimé.
 */
@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    /** Texte d'aide montré au stagiaire. */
    private String aide;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "applies_to_all", nullable = false)
    private boolean appliesToAll;

    @ManyToMany
    @JoinTable(
            name = "document_type_categories",
            joinColumns = @JoinColumn(name = "document_type_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    @Column(name = "deadline_days")
    private Integer deadlineDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_anchor")
    private DeadlineAnchor deadlineAnchor;

    /** Formats acceptés, en minuscules séparés par des virgules ; {@code null} = règle générale. */
    @Column(name = "allowed_formats")
    private String allowedFormats;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
