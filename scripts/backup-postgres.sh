#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
mkdir -p "$BACKUP_DIR"
STAMP="$(date +'%Y%m%d_%H%M%S')"
OUT="$BACKUP_DIR/adcrm_${STAMP}.sql.gz"

echo "[REMAGO FLOW] Создаю резервную копию PostgreSQL: $OUT"
docker compose exec -T postgres pg_dump -U adcrm -d adcrm --no-owner --no-privileges | gzip -9 > "$OUT"
test -s "$OUT"
find "$BACKUP_DIR" -type f -name 'adcrm_*.sql.gz' -mtime "+$RETENTION_DAYS" -delete || true
echo "[REMAGO FLOW] Готово: $(du -h "$OUT" | awk '{print $1}')"
