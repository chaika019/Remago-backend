CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_permissions (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    permission VARCHAR(80) NOT NULL,
    PRIMARY KEY (user_id, permission)
);

CREATE TABLE cities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(10) NOT NULL,
    needs_repair BOOLEAN NOT NULL DEFAULT FALSE,
    stand_address VARCHAR(500),
    stand_format VARCHAR(30),
    repair_status VARCHAR(50) NOT NULL DEFAULT 'NONE',
    repair_plan TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    legal_form VARCHAR(50) NOT NULL,
    short_name VARCHAR(255) NOT NULL,
    canonical_name VARCHAR(350) NOT NULL UNIQUE,
    inn VARCHAR(100) UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_by_id BIGINT REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_companies_canonical_name ON companies (lower(canonical_name));
CREATE INDEX idx_companies_short_name ON companies (lower(short_name));

CREATE TABLE contacts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    fio VARCHAR(255) NOT NULL,
    position VARCHAR(255),
    phone VARCHAR(100),
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_contacts_company ON contacts(company_id);

CREATE TABLE weeks (
    id BIGSERIAL PRIMARY KEY,
    iso_year INTEGER NOT NULL,
    iso_week_number INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    label VARCHAR(255) NOT NULL,
    drive_folder_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_week_year_number UNIQUE (iso_year, iso_week_number)
);
CREATE INDEX idx_weeks_dates ON weeks(start_date, end_date);

CREATE TABLE shifts (
    id BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    week_id BIGINT NOT NULL REFERENCES weeks(id),
    status VARCHAR(80) NOT NULL,
    company_id BIGINT REFERENCES companies(id),
    responsible_person_id BIGINT REFERENCES app_users(id),
    auto_generated_from_shift_id BIGINT REFERENCES shifts(id),
    a4_sold BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_shifts_city_week ON shifts(city_id, week_id);
CREATE INDEX idx_shifts_status ON shifts(status);

CREATE TABLE goal_options (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE deals (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    service_type VARCHAR(50) NOT NULL,
    goal_option_id BIGINT REFERENCES goal_options(id),
    goal_achieved BOOLEAN NOT NULL DEFAULT FALSE,
    called_today VARCHAR(50) NOT NULL,
    next_call_date DATE,
    comment TEXT,
    prospective BOOLEAN NOT NULL DEFAULT FALSE,
    kanban_stage VARCHAR(50) NOT NULL,
    activity_date DATE NOT NULL,
    daily_sequence INTEGER NOT NULL,
    week_id BIGINT NOT NULL REFERENCES weeks(id),
    created_by_id BIGINT NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_deal_daily_sequence UNIQUE (created_by_id, activity_date, daily_sequence)
);
CREATE INDEX idx_deals_week_manager ON deals(week_id, created_by_id);
CREATE INDEX idx_deals_next_call ON deals(next_call_date);

CREATE TABLE deal_target_cities (
    deal_id BIGINT NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    PRIMARY KEY (deal_id, city_id)
);

CREATE TABLE photo_reports (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    drive_file_id VARCHAR(255),
    drive_web_view_link VARCHAR(1000),
    uploaded_by_id BIGINT NOT NULL REFERENCES app_users(id),
    sent_to_client_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_photo_reports_shift ON photo_reports(shift_id);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    author_id BIGINT REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, created_at DESC);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    trigger_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT,
    dedupe_key VARCHAR(500),
    recipient_user_id BIGINT REFERENCES app_users(id),
    recipient_role VARCHAR(50),
    channel VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_user ON notifications(recipient_user_id, created_at DESC);
CREATE UNIQUE INDEX uk_notifications_dedupe_user ON notifications(dedupe_key, recipient_user_id) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notifications_role ON notifications(recipient_role, created_at DESC);
