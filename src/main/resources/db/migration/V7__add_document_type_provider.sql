-- Qui fournit un type de document : le stagiaire (pièce de son inscription) ou l'ADAC (pièce de la formation,
-- déposée par la chargée de formation ou le formateur). Les types existants restent des pièces du stagiaire.
ALTER TABLE document_types
    ADD COLUMN provided_by VARCHAR(20) NOT NULL DEFAULT 'STAGIAIRE',
    ADD CONSTRAINT chk_document_types_provided_by CHECK (provided_by IN ('STAGIAIRE', 'ADAC'));
