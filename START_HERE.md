# REMAGO FLOW — Backend

## Запуск

```bash
docker compose up --build
```

Backend: `http://localhost:8080`
PostgreSQL с хоста: `localhost:5433`
Gotenberg: `http://localhost:3001`

Не используйте `docker compose down -v`, если нужно сохранить существующую БД.

## Миграции

Flyway применяет последовательно:

- V1 — базовая схема;
- V2 — документы и автоматизация продаж;
- V3 — библиотека документов;
- V4 — ручное создание спецификаций без обязательной CRM-сделки.

При существующей БД V1–V3 повторно не выполняются, V4 применится автоматически.

## Регистрация и роли

- Новый пользователь получает `PENDING` и видит только страницу ожидания.
- `SUPER_ADMIN` может назначать любые рабочие роли.
- `ROK` может назначить новому `PENDING`-пользователю только `MANAGER`.
- Email супер-администратора задаётся через `SUPER_ADMIN_EMAILS`.
- Значение по умолчанию: `egorchuiko7@gmail.com`.
- Совпавший существующий пользователь повышается до `SUPER_ADMIN` при запуске приложения.

## Документы

Раздел документов доступен ролям `SUPER_ADMIN`, `BUH`, `DELO`.
Реквизиты исполнителя доступны только `SUPER_ADMIN`.
Договоры и спецификации можно формировать из данных в приложении; DOCX создаётся backend, PDF — через Gotenberg.

## Локальные seed-пользователи

В Docker Compose `SEED_ENABLED=true` по умолчанию для локальной разработки. Пароль seed-пользователей: `ChangeMe123!`.
Для production задайте `SEED_ENABLED=false` и свой `JWT_SECRET_BASE64`.

## Security / email verification update

Before production registration is enabled, read `SECURITY_AND_EMAIL_SETUP.md` and configure `.env` with SMTP credentials and the one-time super-admin bootstrap password if the trusted admin account does not exist yet.
