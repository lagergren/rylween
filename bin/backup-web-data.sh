#!/usr/bin/env bash
set -euo pipefail

DATA_DIR="${RYLWEEN_DATA_DIR:-$HOME/.rylween/data}"
OUTPUT=""
INCLUDE_LOGS=false

usage() {
  cat <<'EOF'
Usage: ./bin/backup-web-data.sh [--data DIR] [--output FILE] [--include-logs]

Creates one compressed archive containing the shared world save and persisted
web auth users. Logs are excluded by default because they can be large and may
contain operational/user activity.

Environment:
  RYLWEEN_DATA_DIR  Data directory to back up. Default: ~/.rylween/data
EOF
}

require_next_arg() {
  local option="$1"
  if [[ $# -lt 2 ]]; then
    echo "$option requires a value" >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  arg="$1"
  shift
  case "$arg" in
    --data)
      require_next_arg "$arg" "$@"
      DATA_DIR="$1"
      shift
      ;;
    --data=*)
      DATA_DIR="${arg#--data=}"
      ;;
    --output)
      require_next_arg "$arg" "$@"
      OUTPUT="$1"
      shift
      ;;
    --output=*)
      OUTPUT="${arg#--output=}"
      ;;
    --include-logs)
      INCLUDE_LOGS=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -d "$DATA_DIR" ]]; then
  echo "Data directory does not exist: $DATA_DIR" >&2
  exit 1
fi

if [[ -z "$OUTPUT" ]]; then
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  OUTPUT="rylween-web-data-$timestamp.tar.gz"
fi

entries=()
[[ -e "$DATA_DIR/saves" ]] && entries+=("saves")
[[ -e "$DATA_DIR/auth/users" ]] && entries+=("auth/users")
if [[ "$INCLUDE_LOGS" == true && -e "$DATA_DIR/logs" ]]; then
  entries+=("logs")
fi

if [[ ${#entries[@]} -eq 0 ]]; then
  echo "Nothing to back up under $DATA_DIR." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
tar -C "$DATA_DIR" -czf "$OUTPUT" "${entries[@]}"
printf '[rylween-backup] wrote %s from %s\n' "$OUTPUT" "$DATA_DIR"
