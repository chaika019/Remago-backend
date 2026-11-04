-- ============================================================
-- Единая библиотека документов.
--
-- V1 и V2 НЕ УДАЛЯТЬ.
-- V3 добавляет хранилище для загружаемых/исторических документов.
-- Существующие contracts и specifications остаются источниками
-- данных и автоматически попадают в GET /api/document-library.
-- ============================================================

CREATE TABLE stored_documents (
    id BIGSERIAL PRIMARY KEY,

    document_type VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,
    number VARCHAR(150),
    document_date DATE,

    company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    deal_id BIGINT REFERENCES deals(id) ON DELETE SET NULL,

    original_file_name VARCHAR(500) NOT NULL,
    stored_path VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(255),

    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_stored_documents_type_date
    ON stored_documents(document_type, document_date DESC);

CREATE INDEX idx_stored_documents_company
    ON stored_documents(company_id);

CREATE INDEX idx_stored_documents_deal
    ON stored_documents(deal_id);
