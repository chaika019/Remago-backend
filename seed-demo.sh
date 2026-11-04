#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# REMAGO FLOW — demo seed for "График ротации"
# Backend: http://localhost:8080
#
# Что делает:
# 1) Логинится под РОПом
# 2) Синхронизирует недели 2026
# 3) Создаёт/обновляет тестовые города
# 4) Создаёт/обновляет смены на неделях 34–41
# 5) Можно запускать повторно — существующие ячейки обновляются,
#    а не дублируются.
# ============================================================

API_URL="${API_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-rop@adcrm.local}"
PASSWORD="${PASSWORD:-ChangeMe123!}"
YEAR="${YEAR:-2026}"
WEEK_FROM="${WEEK_FROM:-34}"
WEEK_TO="${WEEK_TO:-41}"

command -v curl >/dev/null 2>&1 || {
  echo "❌ Не найден curl. Установи: sudo apt install -y curl"
  exit 1
}

command -v python3 >/dev/null 2>&1 || {
  echo "❌ Не найден python3. Установи: sudo apt install -y python3"
  exit 1
}

json_get() {
  local expr="$1"
  python3 -c '
import json, sys
expr = sys.argv[1]
data = json.load(sys.stdin)

cur = data
for part in expr.split("."):
    if not part:
        continue
    if isinstance(cur, list):
        cur = cur[int(part)]
    else:
        cur = cur.get(part)

if cur is None:
    print("")
elif isinstance(cur, (dict, list)):
    print(json.dumps(cur, ensure_ascii=False))
else:
    print(cur)
' "$expr"
}

api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [[ -n "$body" ]]; then
    curl -fsS \
      -X "$method" \
      "$API_URL$path" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      --data "$body"
  else
    curl -fsS \
      -X "$method" \
      "$API_URL$path" \
      -H "Authorization: Bearer $TOKEN"
  fi
}

echo
echo "============================================================"
echo "  REMAGO FLOW — заполнение демо-графика"
echo "============================================================"
echo

echo "🔎 Проверяю backend..."
if ! curl -fsS "$API_URL/actuator/health" >/dev/null; then
  echo "❌ Backend недоступен: $API_URL"
  echo "   Проверь, что docker compose запущен."
  exit 1
fi
echo "✅ Backend online"

echo
echo "🔐 Вход: $EMAIL"
LOGIN_RESPONSE="$(curl -fsS \
  -X POST "$API_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  --data "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"

TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | json_get "accessToken")"

if [[ -z "$TOKEN" ]]; then
  echo "❌ Не удалось получить JWT."
  echo "$LOGIN_RESPONSE"
  exit 1
fi
echo "✅ JWT получен"

echo
echo "📅 Синхронизирую недели $YEAR..."
api POST "/api/weeks/sync?year=$YEAR" >/dev/null
echo "✅ Недели готовы"

# ------------------------------------------------------------
# Города
# Формат:
# name|category|needsRepair|address|format|repairStatus|repairPlan
# В backend категории: A / B / V
# ------------------------------------------------------------
CITIES=(
  "Минск|A|false|пр-т Победителей, 21|A4|NONE|"
  "Брест|A|false|пр-т Машерова, 17|A4|NONE|"
  "Гродно|A|false|ул. Советская, 12|A4|NONE|"
  "Витебск|B|false|ул. Ленина, 45|A4|NONE|"
  "Гомель|B|false|пр-т Ленина, 30|A4|NONE|"
  "Могилёв|B|false|ул. Первомайская, 28|A4|NONE|"
  "Барановичи|B|false|ул. Комсомольская, 8|A4|NONE|"
  "Пинск|B|false|ул. Первомайская, 66|A4|NONE|"
  "Бобруйск|V|true|ул. Минская, 54|A4|IN_PROGRESS|Ремонт панели стенда и кармашков"
  "Орша|V|true|ул. Мира, 11|A4|PLANNED|Замена повреждённых элементов стенда"
)

declare -A CITY_IDS

get_city_id_by_name() {
  local name="$1"
  local cities_json="$2"

  printf '%s' "$cities_json" | python3 -c '
import json, sys
name = sys.argv[1]
data = json.load(sys.stdin)
for city in data:
    if city.get("name") == name:
        print(city.get("id", ""))
        break
' "$name"
}

echo
echo "🏙  Создаю/обновляю города..."

EXISTING_CITIES="$(api GET "/api/cities")"

for row in "${CITIES[@]}"; do
  IFS='|' read -r name category repair address format repair_status repair_plan <<< "$row"

  city_id="$(get_city_id_by_name "$name" "$EXISTING_CITIES")"

  body="$(python3 - "$name" "$category" "$repair" "$address" "$format" "$repair_status" "$repair_plan" <<'PY'
import json, sys
name, category, repair, address, stand_format, repair_status, repair_plan = sys.argv[1:]
print(json.dumps({
    "name": name,
    "category": category,
    "needsRepair": repair.lower() == "true",
    "standAddress": address,
    "standFormat": stand_format,
    "repairStatus": repair_status,
    "repairPlan": repair_plan if repair_plan else None
}, ensure_ascii=False))
PY
)"

  if [[ -n "$city_id" ]]; then
    response="$(api PUT "/api/cities/$city_id" "$body")"
    echo "  ↻ $name — обновлён"
  else
    response="$(api POST "/api/cities" "$body")"
    city_id="$(printf '%s' "$response" | json_get "id")"
    echo "  + $name — создан"
  fi

  CITY_IDS["$name"]="$city_id"
done

echo "✅ Города готовы"

# ------------------------------------------------------------
# Получаем недели и связываем isoWeekNumber -> id
# ------------------------------------------------------------
WEEKS_JSON="$(api GET "/api/weeks?year=$YEAR")"

get_week_id() {
  local week_number="$1"

  printf '%s' "$WEEKS_JSON" | python3 -c '
import json, sys
week_no = int(sys.argv[1])
data = json.load(sys.stdin)
for week in data:
    if week.get("isoWeekNumber") == week_no:
        print(week.get("id", ""))
        break
' "$week_number"
}

# ------------------------------------------------------------
# Смена: city|week|status|a4Sold|failureReason
#
# WHITE_SHIFT обязательно a4Sold=true по backend-правилу.
# ------------------------------------------------------------
SHIFTS=(
  "Минск|34|SHIFT_AND_SALE|false|"
  "Минск|35|ACTIVE_SELL|false|"
  "Минск|36|SHIFT_AND_NOTIFICATION|false|"
  "Минск|37|AUTO_SHIFT|false|"
  "Минск|38|AUTO_AND_SHIFT|false|"
  "Минск|39|SHIFT_AND_SALE|false|"
  "Минск|40|ACTIVE_SELL|false|"
  "Минск|41|SHIFT_AND_NOTIFICATION|false|"

  "Брест|34|WHITE_SHIFT|true|"
  "Брест|35|SHIFT_AND_SALE|false|"
  "Брест|36|ACTIVE_SELL|false|"
  "Брест|37|PLUS_MINUS|false|Неполный комплект материалов"
  "Брест|38|SHIFT_AND_NOTIFICATION|false|"
  "Брест|39|AUTO_SHIFT|false|"
  "Брест|40|AUTO_AND_SHIFT|false|"

  "Гродно|34|AUTO_SHIFT|false|"
  "Гродно|35|AUTO_AND_SHIFT|false|"
  "Гродно|36|SHIFT_AND_SALE|false|"
  "Гродно|37|ACTIVE_SELL|false|"
  "Гродно|38|SHIFT_AND_NOTIFICATION|false|"
  "Гродно|40|WHITE_SHIFT|true|"

  "Витебск|34|SHIFT_AND_SALE|false|"
  "Витебск|35|ACTIVE_SELL|false|"
  "Витебск|36|NOT_OCCURRED|false|Город не продан"
  "Витебск|37|SHIFT_AND_SALE|false|"
  "Витебск|38|ACTIVE_SELL|false|"

  "Гомель|34|ACTIVE_SELL|false|"
  "Гомель|35|SHIFT_AND_NOTIFICATION|false|"
  "Гомель|36|AUTO_SHIFT|false|"
  "Гомель|37|AUTO_AND_SHIFT|false|"
  "Гомель|38|SHIFT_AND_SALE|false|"

  "Могилёв|34|PLUS_MINUS|false|Смена выполнена со смещением даты"
  "Могилёв|35|SHIFT_AND_SALE|false|"
  "Могилёв|36|ACTIVE_SELL|false|"
  "Могилёв|37|MINUS|false|Сменщик не вышел на объект"
  "Могилёв|38|SHIFT_AND_SALE|false|"

  "Барановичи|34|SHIFT_AND_NOTIFICATION|false|"
  "Барановичи|35|AUTO_SHIFT|false|"
  "Барановичи|36|AUTO_AND_SHIFT|false|"
  "Барановичи|37|SHIFT_AND_SALE|false|"
  "Барановичи|38|ACTIVE_SELL|false|"

  "Пинск|34|SHIFT_AND_SALE|false|"
  "Пинск|35|ACTIVE_SELL|false|"
  "Пинск|36|PLUS_MINUS|false|Не хватило визиток"
  "Пинск|37|SHIFT_AND_NOTIFICATION|false|"
  "Пинск|38|AUTO_SHIFT|false|"
)

# Получаем текущую сетку один раз перед заполнением.
SCHEDULE_JSON="$(api GET "/api/schedule?year=$YEAR&weekFrom=$WEEK_FROM&weekTo=$WEEK_TO")"

get_existing_shift_id() {
  local city_id="$1"
  local week_number="$2"

  printf '%s' "$SCHEDULE_JSON" | python3 -c '
import json, sys
city_id = int(sys.argv[1])
week_no = int(sys.argv[2])
data = json.load(sys.stdin)

for row in data.get("rows", []):
    if row.get("cityId") != city_id:
        continue
    for cell in row.get("cells", []):
        if cell.get("isoWeekNumber") == week_no:
            sid = cell.get("shiftId")
            if sid is not None:
                print(sid)
            raise SystemExit
' "$city_id" "$week_number"
}

echo
echo "🗓  Заполняю график ротации $YEAR, недели $WEEK_FROM–$WEEK_TO..."

created=0
updated=0

for row in "${SHIFTS[@]}"; do
  IFS='|' read -r city week_no status a4_sold reason <<< "$row"

  city_id="${CITY_IDS[$city]}"
  week_id="$(get_week_id "$week_no")"

  if [[ -z "$week_id" ]]; then
    echo "  ⚠ Пропуск: не найдена неделя $week_no"
    continue
  fi

  shift_id="$(get_existing_shift_id "$city_id" "$week_no")"

  if [[ -n "$shift_id" ]]; then
    update_body="$(python3 - "$status" "$a4_sold" "$reason" <<'PY'
import json, sys
status, a4_sold, reason = sys.argv[1:]
print(json.dumps({
    "status": status,
    "a4Sold": a4_sold.lower() == "true",
    "failureReason": reason if reason else None
}, ensure_ascii=False))
PY
)"
    api PATCH "/api/shifts/$shift_id" "$update_body" >/dev/null
    ((updated+=1))
    echo "  ↻ $city / нед. $week_no → $status"
  else
    create_body="$(python3 - "$city_id" "$week_id" "$status" "$a4_sold" "$reason" <<'PY'
import json, sys
city_id, week_id, status, a4_sold, reason = sys.argv[1:]
print(json.dumps({
    "cityId": int(city_id),
    "weekId": int(week_id),
    "status": status,
    "companyId": None,
    "responsiblePersonId": None,
    "a4Sold": a4_sold.lower() == "true",
    "failureReason": reason if reason else None
}, ensure_ascii=False))
PY
)"
    api POST "/api/shifts" "$create_body" >/dev/null
    ((created+=1))
    echo "  + $city / нед. $week_no → $status"
  fi
done

echo
echo "============================================================"
echo "✅ DEMO готов"
echo "   Города: ${#CITIES[@]}"
echo "   Новых смен: $created"
echo "   Обновлено смен: $updated"
echo "============================================================"
echo
echo "Теперь открой/обнови:"
echo "  http://localhost:5173"
echo
echo "На странице «График ротации» выбери недели $WEEK_FROM–$WEEK_TO."
echo "Категория В отобразится как «Ремонт и реконструкция»."
echo
