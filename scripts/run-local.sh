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

required_variables=(
  DIET_ASSISTANT_DB_URL
  DIET_ASSISTANT_DB_USERNAME
  DIET_ASSISTANT_DB_PASSWORD
)

missing_variables=()

for variable_name in "${required_variables[@]}"; do
  if ! grep -Eq "^${variable_name}=.+" .env; then
    missing_variables+=("$variable_name")
  fi
done

if (( ${#missing_variables[@]} > 0 )); then
  echo "The following required .env variables are missing or empty:" >&2
  printf '  %s\n' "${missing_variables[@]}" >&2
  exit 1
fi

while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" == \#* ]] && continue
  [[ "$line" != *=* ]] && continue

  variable_name="${line%%=*}"
  variable_value="${line#*=}"

  case "$variable_name" in
    DIET_ASSISTANT_DB_URL|DIET_ASSISTANT_DB_USERNAME|DIET_ASSISTANT_DB_PASSWORD)
      export "$variable_name=$variable_value"
      ;;
  esac
done < .env

export SPRING_PROFILES_ACTIVE=local

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
