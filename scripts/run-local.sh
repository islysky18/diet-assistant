#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if JAVA_21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
    export JAVA_HOME="$JAVA_21_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

if ! java -version 2>&1 | head -n 1 | grep -Eq 'version "21\.|openjdk version "21\.'; then
  cat >&2 <<'MESSAGE'
Java 21 is required to run this application.

Install Java 21 and ensure it is first on PATH, or on macOS run:
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export PATH="$JAVA_HOME/bin:$PATH"
MESSAGE
  exit 1
fi

if [[ ! -f .env ]]; then
  cat >&2 <<'MESSAGE'
Missing project-root .env file.

Create it before running the application:
  cp .env.example .env

Then edit .env and replace placeholder values with local-only credentials.
MESSAGE
  exit 1
fi

line_number=0
while IFS= read -r line || [[ -n "$line" ]]; do
  ((line_number += 1))
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue

  if [[ "$line" != *=* ]]; then
    echo "Invalid .env entry on line $line_number: expected NAME=value." >&2
    exit 1
  fi

  variable_name="${line%%=*}"
  variable_value="${line#*=}"

  if [[ ! "$variable_name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid .env variable name on line $line_number." >&2
    exit 1
  fi

  if [[ "$variable_name" == DIET_ASSISTANT_* || "$variable_name" == USDA_FDC_API_KEY ]]; then
    export "$variable_name=$variable_value"
  fi
done < .env

required_variables=(
  DIET_ASSISTANT_DB_URL
  DIET_ASSISTANT_DB_USERNAME
  DIET_ASSISTANT_DB_PASSWORD
)

missing_variables=()

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    missing_variables+=("$variable_name")
  fi
done

if (( ${#missing_variables[@]} > 0 )); then
  echo "The following required .env variables are missing or empty:" >&2
  printf '  %s\n' "${missing_variables[@]}" >&2
  exit 1
fi

# Map the application's .env names to Spring Boot's standard datasource
# properties so a fresh clone does not depend on an ignored
# application-local.yml file.
export SPRING_DATASOURCE_URL="$DIET_ASSISTANT_DB_URL"
export SPRING_DATASOURCE_USERNAME="$DIET_ASSISTANT_DB_USERNAME"
export SPRING_DATASOURCE_PASSWORD="$DIET_ASSISTANT_DB_PASSWORD"

export SPRING_PROFILES_ACTIVE=local

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
