-- Interim hand-written schema for local dev/test, matching docs/DB_MODEL.mmd.
-- Superseded by a Flyway V1 migration in TICKET-004 — do not hand-edit once that lands.
-- Idempotent (IF NOT EXISTS everywhere) so it can run on every app boot without erroring.

CREATE TABLE IF NOT EXISTS users (
    id                            BIGSERIAL PRIMARY KEY,
    email                         VARCHAR(255) NOT NULL UNIQUE,
    password_hash                 VARCHAR(255) NOT NULL,
    nom                           VARCHAR(255) NOT NULL,
    prenom                        VARCHAR(255) NOT NULL,
    role                          VARCHAR(50)  NOT NULL,
    is_active                     BOOLEAN NOT NULL DEFAULT TRUE,
    email_notifications_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS formations (
    id            BIGSERIAL PRIMARY KEY,
    intitule      VARCHAR(255) NOT NULL,
    description   TEXT,
    date_debut    DATE NOT NULL,
    date_fin      DATE NOT NULL,
    modalite      VARCHAR(50) NOT NULL,
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    formateur_id  BIGINT REFERENCES users(id),
    created_by    BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT chk_formations_date_order CHECK (date_fin >= date_debut)
);

CREATE TABLE IF NOT EXISTS inscriptions (
    id            BIGSERIAL PRIMARY KEY,
    stagiaire_id  BIGINT NOT NULL REFERENCES users(id),
    formation_id  BIGINT NOT NULL REFERENCES formations(id),
    inscrit_le    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inscriptions_stagiaire_formation UNIQUE (stagiaire_id, formation_id)
);

CREATE TABLE IF NOT EXISTS documents (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_url        TEXT NOT NULL,
    file_size       BIGINT NOT NULL,
    mime_type       VARCHAR(255) NOT NULL,
    uploaded_by     BIGINT NOT NULL REFERENCES users(id),
    formation_id    BIGINT REFERENCES formations(id),
    inscription_id  BIGINT REFERENCES inscriptions(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_documents_exactly_one_target CHECK (
        (formation_id IS NOT NULL AND inscription_id IS NULL) OR
        (formation_id IS NULL AND inscription_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS messages (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT NOT NULL,
    sender_id   BIGINT NOT NULL REFERENCES users(id),
    is_group    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS message_recipients (
    id            BIGSERIAL PRIMARY KEY,
    message_id    BIGINT NOT NULL REFERENCES messages(id),
    recipient_id  BIGINT NOT NULL REFERENCES users(id),
    read_at       TIMESTAMPTZ,
    CONSTRAINT uk_message_recipients_message_recipient UNIQUE (message_id, recipient_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id                  BIGSERIAL PRIMARY KEY,
    recipient_id        BIGINT NOT NULL REFERENCES users(id),
    type                VARCHAR(50) NOT NULL,
    content             VARCHAR(255) NOT NULL,
    entity_type         VARCHAR(50),
    entity_id           BIGINT,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_from_bell   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS activation_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    code_hash   VARCHAR(255) NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    type        VARCHAR(50) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes recommended in docs/DB_MODEL.md
CREATE INDEX IF NOT EXISTS idx_formations_formateur_id ON formations(formateur_id);
CREATE INDEX IF NOT EXISTS idx_formations_created_by ON formations(created_by);
CREATE INDEX IF NOT EXISTS idx_inscriptions_stagiaire_id ON inscriptions(stagiaire_id);
CREATE INDEX IF NOT EXISTS idx_inscriptions_formation_id ON inscriptions(formation_id);
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by ON documents(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_documents_formation_id ON documents(formation_id);
CREATE INDEX IF NOT EXISTS idx_documents_inscription_id ON documents(inscription_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_message_recipients_message_id ON message_recipients(message_id);
CREATE INDEX IF NOT EXISTS idx_message_recipients_recipient_read_at ON message_recipients(recipient_id, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_is_read ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_activation_tokens_user_created_at ON activation_tokens(user_id, created_at);
