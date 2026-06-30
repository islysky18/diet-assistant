#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

if [[ ! -f .env ]]; then
  cat >&2 <<'MESSAGE'
Missing project-root .env file.

Create it before starting the local database:
  cp .env.example .env

Then edit .env and replace placeholder values with local-only credentials.
MESSAGE
  exit 1
fi

required_variables=(
  DIET_ASSISTANT_DB_NAME
  DIET_ASSISTANT_DB_USERNAME
  DIET_ASSISTANT_DB_PASSWORD
  DIET_ASSISTANT_DB_ROOT_PASSWORD
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

docker compose up -d
docker compose ps
