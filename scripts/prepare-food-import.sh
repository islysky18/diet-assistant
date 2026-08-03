#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
IMPORT_ID="${1:-}"

if [[ ! "$IMPORT_ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]]; then
  echo "Usage: ./scripts/prepare-food-import.sh <pending-import-uuid>" >&2
  exit 1
fi

IMPORT_DIR="$REPO_ROOT/data/pending-food-imports/$IMPORT_ID"
METADATA_FILE="$IMPORT_DIR/metadata.json"
RESULT_FILE="$IMPORT_DIR/result.json"

if [[ ! -f "$METADATA_FILE" || ! -f "$IMPORT_DIR/nutrition.jpg" ]]; then
  echo "Pending import not found: $IMPORT_ID" >&2
  exit 1
fi

echo "Pending food import: $IMPORT_ID"
echo "Inspect metadata: $METADATA_FILE"
echo "Inspect nutrition.jpg and optional front.jpg in: $IMPORT_DIR"
echo "Do not inspect or depend on the *-original temporary files."
echo "Write the structured result to: $RESULT_FILE"
echo
echo "Ask Codex:"
echo "  Inspect pending food import $IMPORT_ID. Read both product photos, then write result.json using the schema below."
echo "  Transcribe only visible values. Use null for every missing or uncertain value; never guess and never replace missing values with 0."
echo
echo 'Result schema:'
echo '{'
echo '  "name": null,'
echo '  "brand": null,'
echo '  "referenceAmount": null,'
echo '  "referenceUnit": null,'
echo '  "referenceWeightGrams": null,'
echo '  "calories": null,'
echo '  "proteinGrams": null,'
echo '  "carbohydrateGrams": null,'
echo '  "fatGrams": null,'
echo '  "fiberGrams": null,'
echo '  "notes": null'
echo '}'
