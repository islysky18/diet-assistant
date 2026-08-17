#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
UI_TEST_PORT="${UI_TEST_PORT:-18080}"
DB_CONTAINER="diet-assistant-ui-test-$(date +%s)-$$"
DB_CONTAINER_ID=""
DB_PASSWORD="ui_test_password"
APP_LOG="${TMPDIR:-/tmp}/diet-assistant-ui-test-app-$$.log"
IMPORT_DIR=""
APP_PID=""

cleanup() {
  if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [[ -n "$DB_CONTAINER_ID" ]]; then
    docker rm -f "$DB_CONTAINER_ID" >/dev/null 2>&1 || true
  fi
  if [[ -n "$IMPORT_DIR" ]]; then
    rmdir "$IMPORT_DIR" >/dev/null 2>&1 || true
  fi
  rm -f "$APP_LOG"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cd "$REPO_ROOT"

command -v docker >/dev/null || { echo "Docker is required for UI tests." >&2; exit 1; }
command -v npm >/dev/null || { echo "Node.js and npm are required for UI tests." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required for UI tests." >&2; exit 1; }

if command -v lsof >/dev/null && lsof -nP -iTCP:"$UI_TEST_PORT" -sTCP:LISTEN >/dev/null; then
  echo "UI test port $UI_TEST_PORT is already in use. Stop that process or set UI_TEST_PORT to a free port." >&2
  exit 1
fi

DB_CONTAINER_ID="$(docker run --detach --name "$DB_CONTAINER" \
  --env MYSQL_DATABASE=diet_assistant_ui \
  --env MYSQL_USER=diet_assistant_ui \
  --env MYSQL_PASSWORD="$DB_PASSWORD" \
  --env MYSQL_ROOT_PASSWORD="$DB_PASSWORD" \
  --health-cmd='mysqladmin ping -h localhost -u root -p"$MYSQL_ROOT_PASSWORD"' \
  --health-interval=2s --health-timeout=3s --health-retries=30 \
  --publish 127.0.0.1::3306 mysql:8.4)"

for _ in {1..60}; do
  [[ "$(docker inspect --format='{{.State.Health.Status}}' "$DB_CONTAINER_ID")" == "healthy" ]] && break
  sleep 1
done
[[ "$(docker inspect --format='{{.State.Health.Status}}' "$DB_CONTAINER_ID")" == "healthy" ]] || {
  docker logs "$DB_CONTAINER_ID" >&2
  echo "UI test database did not become healthy." >&2
  exit 1
}

DB_PORT="$(docker port "$DB_CONTAINER_ID" 3306/tcp | sed 's/.*://')"
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:${DB_PORT}/diet_assistant_ui"
export SPRING_DATASOURCE_USERNAME="diet_assistant_ui"
export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
export DIET_ASSISTANT_FOOD_PHOTO_RECOGNIZER_ENABLED=false
IMPORT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/diet-assistant-ui-imports.XXXXXX")"
export DIET_ASSISTANT_PENDING_FOOD_IMPORT_DIRECTORY="$IMPORT_DIR"
export SERVER_PORT="$UI_TEST_PORT"
export UI_TEST_BASE_URL="http://127.0.0.1:${UI_TEST_PORT}"

./mvnw spring-boot:run >"$APP_LOG" 2>&1 &
APP_PID=$!

for _ in {1..120}; do
  curl --fail --silent "$UI_TEST_BASE_URL/foods" >/dev/null 2>&1 && break
  kill -0 "$APP_PID" 2>/dev/null || { cat "$APP_LOG" >&2; exit 1; }
  sleep 1
done
curl --fail --silent "$UI_TEST_BASE_URL/foods" >/dev/null || {
  cat "$APP_LOG" >&2
  echo "UI test application did not become ready." >&2
  exit 1
}

npm run test:ui:browser
