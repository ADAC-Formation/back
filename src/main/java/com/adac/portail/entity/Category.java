package com.adac.portail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * A formation's category (e.g. "Estime de soi en travail social") — see docs/DB_MODEL.md.
 * Never deleted, only deactivated ({@link #isActive}): a formation, even archived, must always
 * be able to resolve its category.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    /** Hex format {@code #RRGGBB} — see docs/DB_MODEL.md. */
    @Column(nullable = false, length = 7)
    private String couleur;

    // Named isActive (not active), matching User.isActive: Lombok still generates isActive()/
    // setActive(boolean) for a boolean field named this way (see UserServiceImpl.deactivate's
    // setActive(false) call) — same convention across entities.
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
