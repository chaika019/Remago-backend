-- ============================================================
-- Спецификации + автоматизация документов
-- ВАЖНО: стартовый номер спецификации = 101, потому что пример имеет №100.
-- Если фактический следующий номер другой, поменяй INSERT document_sequences ниже
-- ДО первого запуска этой миграции.
-- ============================================================

ALTER TABLE companies ADD COLUMN legal_address TEXT;
ALTER TABLE companies ADD COLUMN postal_address TEXT;
ALTER TABLE companies ADD COLUMN settlement_account VARCHAR(255);
ALTER TABLE companies ADD COLUMN bank_name VARCHAR(255);
ALTER TABLE companies ADD COLUMN bank_bic VARCHAR(100);

ALTER TABLE contacts ADD COLUMN telegram_chat_id VARCHAR(255);
ALTER TABLE contacts ADD COLUMN viber_user_id VARCHAR(255);
ALTER TABLE contacts ADD COLUMN preferred_delivery_channel VARCHAR(30);

ALTER TABLE deals ADD COLUMN document_intent VARCHAR(30) NOT NULL DEFAULT 'NONE';

CREATE TABLE organization_profiles (
    id BIGSERIAL PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    unp VARCHAR(50) NOT NULL,
    settlement_account VARCHAR(255) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    bank_bic VARCHAR(100) NOT NULL,
    bank_city VARCHAR(255),
    legal_address TEXT NOT NULL,
    postal_address TEXT NOT NULL,
    registration_info TEXT,
    signatory_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Реквизиты исполнителя взяты из приложенного бланка спецификации.
-- registration_info не заполняем: этих данных в самом бланке нет.
INSERT INTO organization_profiles (
    legal_name, unp, settlement_account, bank_name, bank_bic, bank_city,
    legal_address, postal_address, registration_info, signatory_name,
    created_at, updated_at
) VALUES (
    'Общество с ограниченной ответственностью "Ремаго"',
    '691341415',
    'BY14ALFA30122A56360010270000',
    'ЗАО «Альфа-Банк»',
    'ALFABY2X',
    'г. Минск',
    'Республика Беларусь, 223311, Минская область, г. Березино, ул. Новобазарная, д.4а',
    '220004, Республика Беларусь, г. Минск, административно-бытовой корпус, литер А 1-6/к-кп, по ул. Короля, 2, помещение 215',
    NULL,
    'Д.А. Лабкович',
    now(), now()
);

CREATE TABLE contracts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    number VARCHAR(100) NOT NULL,
    contract_date DATE NOT NULL,
    valid_until DATE,
    auto_renewal BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    docx_path VARCHAR(1000),
    pdf_path VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_contract_company_number UNIQUE (company_id, number)
);
CREATE INDEX idx_contract_company_active ON contracts(company_id, active, contract_date DESC);

CREATE TABLE deal_placements (
    id BIGSERIAL PRIMARY KEY,
    deal_id BIGINT NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    stand_format VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    discount_percent NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (discount_percent >= 0 AND discount_percent <= 100),
    promotion_name VARCHAR(255),
    vat_exempt BOOLEAN NOT NULL DEFAULT TRUE,
    vat_rate NUMERIC(6,2) NOT NULL DEFAULT 0 CHECK (vat_rate >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_deal_placements_deal ON deal_placements(deal_id, period_start);

CREATE TABLE document_sequences (
    code VARCHAR(50) PRIMARY KEY,
    next_value BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
INSERT INTO document_sequences(code, next_value, updated_at)
VALUES ('SPECIFICATION', 101, now());

CREATE TABLE specifications (
    id BIGSERIAL PRIMARY KEY,
    number BIGINT NOT NULL UNIQUE,
    specification_date DATE NOT NULL,
    deal_id BIGINT NOT NULL REFERENCES deals(id),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    contract_id BIGINT REFERENCES contracts(id),
    total_before_discount NUMERIC(15,2) NOT NULL,
    total_after_discount NUMERIC(15,2) NOT NULL,
    vat_amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    docx_path VARCHAR(1000),
    pdf_path VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_specifications_deal ON specifications(deal_id, created_at DESC);
CREATE INDEX idx_specifications_company ON specifications(company_id, created_at DESC);

CREATE TABLE specification_items (
    id BIGSERIAL PRIMARY KEY,
    specification_id BIGINT NOT NULL REFERENCES specifications(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    city_name VARCHAR(255) NOT NULL,
    stand_format VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL,
    discount_percent NUMERIC(6,2) NOT NULL,
    promotion_name VARCHAR(255),
    vat_exempt BOOLEAN NOT NULL,
    vat_rate NUMERIC(6,2) NOT NULL,
    vat_amount NUMERIC(15,2) NOT NULL,
    total_before_discount NUMERIC(15,2) NOT NULL,
    total_after_discount NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_spec_items_specification ON specification_items(specification_id, line_number);

CREATE TABLE document_deliveries (
    id BIGSERIAL PRIMARY KEY,
    specification_id BIGINT NOT NULL REFERENCES specifications(id) ON DELETE CASCADE,
    contact_id BIGINT REFERENCES contacts(id),
    channel VARCHAR(30) NOT NULL,
    recipient VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    sent_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_document_deliveries_spec ON document_deliveries(specification_id, created_at DESC);
