-- Revocable server sessions, login audit and email 2FA challenges.
CREATE TABLE user_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    ip_address VARCHAR(100),
    user_agent VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_user_sessions_user_active ON user_sessions(user_id, revoked_at, expires_at DESC);

CREATE TABLE auth_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    email VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(100),
    user_agent VARCHAR(1000),
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_attempts_created ON auth_attempts(created_at DESC);
CREATE INDEX idx_auth_attempts_user ON auth_attempts(user_id, created_at DESC);

CREATE TABLE two_factor_challenges (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    ip_address VARCHAR(100),
    user_agent VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_two_factor_user ON two_factor_challenges(user_id, created_at DESC);
