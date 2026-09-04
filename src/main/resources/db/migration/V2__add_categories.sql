-- TICKET-046 — introduce categories, and formations.category_id (see docs/DB_MODEL.mmd).
-- formations already exists (V1) and may already hold rows in dev, so category_id can't be
-- added NOT NULL directly: add nullable, backfill, then tighten to NOT NULL + FK, per the
-- ticket's plan.

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(255) NOT NULL,
    couleur     VARCHAR(7) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT chk_categories_couleur_hex CHECK (couleur ~ '^#[0-9A-Fa-f]{6}$')
);

-- Expression index, not a plain UNIQUE(nom): the business rule (TICKET-047 AC) is
-- case-insensitive uniqueness — CategoryRepository.existsByNomIgnoreCase generates
-- `upper(nom) = upper(?)`, and Spring's pre-check alone is a TOCTOU race between two concurrent
-- POSTs (review, TICKET-046). This index matches that predicate exactly and is what actually
-- turns the constraint into a database-level guarantee, catchable as
-- DataIntegrityViolationException in TICKET-047's create/rename.
CREATE UNIQUE INDEX uk_categories_nom_upper ON categories (UPPER(nom));

-- Seed — couleurs provisoires (random), à remplacer plus tard par les couleurs officielles du
-- front (voir TICKET-046 description) : ne pas bloquer dessus.
INSERT INTO categories (nom, couleur) VALUES
    ('Estime de soi en travail social', '#271BB4'),
    ('Méthodologie d''intervention sociale', '#CCCC10'),
    ('Difficultés budgétaires, surendettement', '#1475DC'),
    ('Mieux-être au travail', '#750317'),
    ('Spécial BCP', '#4DCEB0'),
    ('Formation en intra', '#107A14');

ALTER TABLE formations ADD COLUMN category_id BIGINT;

-- Backfill: first seeded category (lowest id) — see ticket's plan, step 3. No formations exist
-- in a fresh environment, so this is a no-op there; in dev, existing rows land on "Estime de soi
-- en travail social" as a placeholder until reassigned via the CRUD (TICKET-047/022).
UPDATE formations
SET category_id = (SELECT MIN(id) FROM categories)
WHERE category_id IS NULL;

ALTER TABLE formations
    ALTER COLUMN category_id SET NOT NULL,
    ADD CONSTRAINT fk_formations_category FOREIGN KEY (category_id) REFERENCES categories(id);

CREATE INDEX idx_formations_category_id ON formations(category_id);
