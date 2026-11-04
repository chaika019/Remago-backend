# REMAGO FLOW — Backend

Backend для графика ротации, CRM, документооборота и уведомлений REMAGO FLOW.

## Стек

- Java 21
- Spring Boot 4.1.1
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Apache POI (DOCX)
- Gotenberg (DOCX → PDF)
- Google Drive API (опционально)
- Docker Compose

## Архитектура

Код организован по бизнес-модулям, а не по общим слоям:

```text
com.adrotation.crm
├── auth            # вход, JWT, текущий пользователь
├── user            # пользователи, роли, permissions
├── city            # города, категории, ремонт
├── company         # компании и контакты
├── crm             # сделки, цели, размещения, CRM reminder
├── schedule        # недели, смены, мастер-график
├── documents       # договоры, спецификации, библиотека документов
├── notification    # внутренние уведомления
├── reports         # фотоотчёты
├── audit           # аудит изменений
├── dashboard       # агрегированные показатели
├── shared          # общие исключения, mapper, Drive abstraction
├── config          # системная конфигурация
└── AdCrmApplication.java
```

## Быстрый запуск

Создайте `.env` из примера:

```bash
cp .env.example .env
```

Запустите:

```bash
docker compose up --build
```

Docker-сборка запускает `mvn package`, то есть компиляцию и тесты. Если сборка прошла, backend будет доступен на:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/actuator/health
```

PostgreSQL с хоста доступен на `localhost:5433`.

## Тестовые пользователи

При Docker Compose по умолчанию `SEED_ENABLED=true`.

Пароль для тестовых пользователей:

```text
ChangeMe123!
```

Пользователи:

```text
director@adcrm.local
rop@adcrm.local
manager@adcrm.local
lawyer@adcrm.local
shiftworker@adcrm.local
```

Вне локальной разработки отключите seed:

```env
SEED_ENABLED=false
```

и обязательно задайте собственный `JWT_SECRET_BASE64`.

## Flyway

Миграции лежат в:

```text
src/main/resources/db/migration
```

Текущая цепочка:

```text
V1__init.sql
V2__specifications_and_sales_automation.sql
V3__document_library.sql
```

Уже применённые миграции V1/V2/V3 не редактировать. Следующие изменения схемы добавлять как V4, V5 и т.д.

Проверить историю:

```bash
docker exec -it ad-crm-backend-postgres-1 \
  psql -U adcrm -d adcrm \
  -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"
```

Не используйте `docker compose down -v`, если нужно сохранить БД.

## Документы

Документы хранятся в named volume:

```text
adcrm_documents:/app/data/documents
```

Gotenberg доступен внутри Compose как:

```text
http://gotenberg:3000
```

### Библиотека документов

Единая библиотека объединяет:

- автоматически сформированные спецификации (`specifications`);
- договоры (`contracts`);
- загруженные исторические документы (`stored_documents`).

Основные endpoint'ы:

```text
GET  /api/document-library
GET  /api/document-library/summary
POST /api/document-library/upload
GET  /api/document-library/uploaded/{id}/download
```

Фильтры списка:

```text
type
query
companyId
dateFrom
dateTo
page
size
```

Типы документов:

```text
SPECIFICATION
CONTRACT
INVOICE
ACT
ADDITIONAL_AGREEMENT
OTHER
```

### Спецификации

```text
GET  /api/deals/{dealId}/document-offer
POST /api/deals/{dealId}/documents/generate
GET  /api/deals/{dealId}/specifications
GET  /api/specifications/{id}/download/docx
GET  /api/specifications/{id}/download/pdf
POST /api/specifications/{id}/deliver
```

### Договоры

```text
POST /api/contracts
GET  /api/contracts?companyId={companyId}
GET  /api/companies/{companyId}/contracts   # backward-compatible
GET  /api/contracts/{id}/download/docx
GET  /api/contracts/{id}/download/pdf
```

## Реквизиты исполнителя

```text
GET   /api/organization-profile
PATCH /api/organization-profile
```

При первом сохранении профиль создаётся автоматически. Для генерации спецификации заполненные реквизиты обязательны.

## SMTP / Telegram

Telegram:

```env
TELEGRAM_BOT_TOKEN=
```

E-mail использует стандартные Spring Boot `spring.mail.*` properties. Для Docker их удобно передать как переменные `SPRING_MAIL_*`, например:

```env
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=user@example.com
SPRING_MAIL_PASSWORD=secret
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
EMAIL_FROM=user@example.com
```

## Google Drive

Интеграция отключена по умолчанию.

```env
GOOGLE_DRIVE_ENABLED=false
GOOGLE_SERVICE_ACCOUNT_JSON=
GOOGLE_DRIVE_ROOT_FOLDER_ID=
```

`GOOGLE_SERVICE_ACCOUNT_JSON` может быть либо путём к JSON-файлу service account, либо самим JSON-текстом.

## Основные API

```text
POST /api/auth/login
GET  /api/auth/me

GET  /api/schedule
GET  /api/dashboard/summary

GET/POST/PUT /api/cities
GET/POST/PATCH /api/companies
GET/POST/PATCH /api/deals
GET/POST/PATCH /api/shifts

GET  /api/notifications
POST /api/notifications/{id}/read

GET  /api/audit/{entityType}/{entityId}
```

## Проверка проекта

Локально:

```bash
mvn test
mvn package
```

или просто:

```bash
docker compose up --build
```

## Важные изменения текущей версии

- код разложен по feature-модулям;
- Hibernate переведён на `ddl-auto=validate`, схема управляется Flyway;
- document volume реально подключён к backend-контейнеру;
- удалены неиспользуемые repository/service методы;
- исправлена генерация многострочной спецификации: каждая строка копируется из исходного шаблона, а не из уже заполненной первой строки;
- реквизиты исполнителя корректно создаются при первом сохранении;
- добавлена защита от параллельного создания дублирующей смены город/неделя;
- добавлена блокировка ежедневной нумерации CRM;
- ошибки API не раскрывают внутренние stack traces клиенту;
- добавлена очистка файлов при неуспешном сохранении исторического документа;
- добавлены безопасные UTF-8 имена скачиваемых файлов;
- добавлены таймауты для Gotenberg REST client;
- сохранена совместимость старого endpoint договоров и добавлен новый `GET /api/contracts?companyId=...`.
