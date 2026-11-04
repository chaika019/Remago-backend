ALTER TABLE app_users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verification_code_hash VARCHAR(255),
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD COLUMN email_verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN email_verification_last_sent_at TIMESTAMPTZ;

-- Existing accounts predate email verification. Keep them usable after this migration.
-- Every account registered after V5 must pass the six-digit email verification flow.
UPDATE app_users
SET email_verified = TRUE;

-- Email uniqueness must be case-insensitive as well (Foo@x and foo@x are the same identity here).
CREATE UNIQUE INDEX uq_app_users_email_lower ON app_users (lower(email));
