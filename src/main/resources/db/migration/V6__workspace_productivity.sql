-- Workspace productivity: next actions, saved views, document workflow/versioning and import traceability.
ALTER TABLE deals
    ADD COLUMN next_action VARCHAR(500),
    ADD COLUMN next_action_due_date DATE,
    ADD COLUMN next_action_completed_at TIMESTAMPTZ;

CREATE INDEX idx_deals_next_action_due
    ON deals(next_action_due_date)
    WHERE next_action_completed_at IS NULL;

ALTER TABLE contracts
    ADD COLUMN document_status VARCHAR(40) NOT NULL DEFAULT 'GENERATED';

CREATE TABLE saved_views (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    module VARCHAR(50) NOT NULL,
    name VARCHAR(180) NOT NULL,
    filters_json TEXT NOT NULL,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_saved_view_user_module_name UNIQUE(user_id, module, name)
);
CREATE INDEX idx_saved_views_user_module ON saved_views(user_id, module, created_at DESC);

CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    docx_path VARCHAR(1000),
    pdf_path VARCHAR(1000),
    note VARCHAR(500),
    created_by_id BIGINT REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_document_version UNIQUE(entity_type, entity_id, version_number)
);
CREATE INDEX idx_document_versions_entity ON document_versions(entity_type, entity_id, version_number DESC);

-- Preserve already generated files as v1 when present.
INSERT INTO document_versions(entity_type, entity_id, version_number, docx_path, pdf_path, note, created_by_id, created_at, updated_at)
SELECT 'CONTRACT', c.id, 1, c.docx_path, c.pdf_path, 'Версия до включения версионирования', NULL, now(), now()
FROM contracts c
WHERE c.docx_path IS NOT NULL OR c.pdf_path IS NOT NULL;

INSERT INTO document_versions(entity_type, entity_id, version_number, docx_path, pdf_path, note, created_by_id, created_at, updated_at)
SELECT 'SPECIFICATION', s.id, 1, s.docx_path, s.pdf_path, 'Версия до включения версионирования', NULL, now(), now()
FROM specifications s
WHERE s.docx_path IS NOT NULL OR s.pdf_path IS NOT NULL;

CREATE TABLE import_batches (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(40) NOT NULL,
    source_name VARCHAR(500),
    created_by_id BIGINT REFERENCES app_users(id),
    imported_rows INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
