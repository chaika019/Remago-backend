# REMAGO FLOW — security + email verification setup

## 1. Create `.env`

Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

## 2. Trusted super-admin

The reserved admin address is:

```text
egorchuiko7@gmail.com
```

Public registration can **never** claim this address.

If the trusted admin account does not exist yet, set a one-time bootstrap password in `.env`:

```env
SUPER_ADMIN_INITIAL_PASSWORD=put-a-strong-password-here
```

Use at least 12 characters. The password is used only to create a missing trusted admin. A normal backend restart does not overwrite an existing admin password.

If an old non-admin account already occupies the reserved email, backend disables that account instead of promoting it by email. Delete the conflicting account manually, then restart with `SUPER_ADMIN_INITIAL_PASSWORD` configured.

## 3. Gmail SMTP for six-digit codes

For Gmail, enable 2-Step Verification on the sending Google account and create a Google **App Password**. Do not put the normal Google password into `.env`.

Example:

```env
EMAIL_VERIFICATION_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-sender@gmail.com
MAIL_PASSWORD=your-16-character-app-password
EMAIL_FROM=your-sender@gmail.com
```

SMTP connection timeout defaults to 10 seconds; read and write timeouts default to
30 seconds. Override `MAIL_CONNECTION_TIMEOUT`, `MAIL_READ_TIMEOUT`, and
`MAIL_WRITE_TIMEOUT` in `.env` (milliseconds). Rebuild and recreate the backend
container after changing configuration: `docker compose up -d --build backend`.

If `SocketTimeoutException: Read timed out` persists, check SMTP access from the
backend host/container. Increasing the timeout cannot fix blocked SMTP traffic.
Port 587 uses STARTTLS. For a provider that supports implicit TLS on port 465,
set all four values together:

```env
MAIL_PORT=465
MAIL_SSL_ENABLED=true
MAIL_STARTTLS_ENABLED=false
MAIL_STARTTLS_REQUIRED=false
```

Do not switch only the port: the TLS mode must match it. SMTP property semantics
are documented in the [Angus Mail documentation](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).

Verification rules:

- 6-digit numeric code;
- valid for 10 minutes;
- resend after 60 seconds;
- maximum 5 wrong attempts per code;
- code is stored only as a BCrypt hash, never as plaintext;
- after verification the user remains `PENDING` until a role is assigned.

## 4. Stable PostgreSQL volume

Compose now uses a fixed project/volume identity:

```text
backend_adcrm_pgdata
```

Renaming or moving the project directory no longer creates another database volume.

You can confirm it with:

```bash
docker volume ls | grep backend_adcrm_pgdata
```

Do not use `docker compose down -v` unless you intentionally want to delete the database.

## 5. Start

```bash
docker compose up --build
```

Flyway will apply `V5__email_verification.sql` automatically. Existing users are marked verified so the migration does not lock out current staff. All users registered after V5 must confirm their email.
