-- TICKET-051 — catalogue des types de document (pièces que les stagiaires fournissent).
-- Voir docs/DB_MODEL.md. Aucune donnée insérée : la liste réelle vient de l'ADAC, jamais de la migration.
-- Un type est « toutes les formations » (applies_to_all) ou lié à une ou plusieurs catégories
-- (document_type_categories). Le délai est relatif aux dates de la formation : N jours avant le début
-- (BEFORE_START) ou après la fin (AFTER_END), les deux colonnes ensemble ou aucune.

CREATE TABLE document_types (
    id               BIGSERIAL PRIMARY KEY,
    nom              VARCHAR(255) NOT NULL,
    aide             VARCHAR(500),
    is_sensitive     BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to_all   BOOLEAN NOT NULL,
    deadline_days    INTEGER,
    deadline_anchor  VARCHAR(20),
    allowed_formats  VARCHAR(100),
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT chk_document_types_deadline_pair CHECK (
        (deadline_days IS NULL AND deadline_anchor IS NULL) OR
        (deadline_days IS NOT NULL AND deadline_anchor IS NOT NULL)
    ),
    CONSTRAINT chk_document_types_deadline_days CHECK (deadline_days IS NULL OR deadline_days BETWEEN 0 AND 365),
    CONSTRAINT chk_document_types_deadline_anchor CHECK (deadline_anchor IS NULL OR deadline_anchor IN ('BEFORE_START', 'AFTER_END'))
);

-- Unicité insensible à la casse garantie par la base (même choix que uk_categories_nom_upper) : le contrôle
-- du service seul serait une course entre deux POST simultanés.
CREATE UNIQUE INDEX uk_document_types_nom_upper ON document_types (UPPER(nom));

CREATE TABLE document_type_categories (
    document_type_id  BIGINT NOT NULL REFERENCES document_types(id) ON DELETE CASCADE,
    category_id       BIGINT NOT NULL REFERENCES categories(id),
    PRIMARY KEY (document_type_id, category_id)
);

CREATE INDEX idx_document_type_categories_category_id ON document_type_categories(category_id);
