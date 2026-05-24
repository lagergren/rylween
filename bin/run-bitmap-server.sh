#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
INSTALL_HOME="$ROOT_DIR/runtimes/runtime-bitmap-web/build/install/rylween-bitmap-web"
LAUNCHER="$INSTALL_HOME/bin/rylween-bitmap-web"

DATA_DIR="${RYLWEEN_DATA_DIR:-$HOME/.rylween/data}"
HOST="${RYLWEEN_BITMAP_HOST:-${RYLWEEN_HOST:-127.0.0.1}}"
PORT="${RYLWEEN_BITMAP_PORT:-${RYLWEEN_PORT:-8081}}"
STATE="${RYLWEEN_BITMAP_STATE:-${RYLWEEN_STATE:-fresh-replay}}"
MAP_TILES="${RYLWEEN_BITMAP_MAP_TILES:-6}"
TILE_SIZE="${RYLWEEN_BITMAP_TILE_SIZE:-68}"
RUN_NAME="${RYLWEEN_BITMAP_NAME:-${RYLWEEN_NAME:-}}"
RUN_USER="${RYLWEEN_BITMAP_USER:-${RYLWEEN_USER:-}}"
AUTH_ACCOUNT="${RYLWEEN_AUTH_ACCOUNT:-}"
AUTH_EMAIL="${RYLWEEN_AUTH_EMAIL:-}"
AUTH_PASSWORD="${RYLWEEN_AUTH_PASSWORD:-}"
AUTH_SECURE_COOKIES="${RYLWEEN_AUTH_SECURE_COOKIES:-}"
LOAD_FILE="${RYLWEEN_BITMAP_LOAD_FILE:-${RYLWEEN_LOAD_FILE:-}}"
SAVE_FILE="${RYLWEEN_BITMAP_SAVE_FILE:-${RYLWEEN_SAVE_FILE:-}}"
DEFAULT_SAVE_FILE="$DATA_DIR/saves/world.rsave"
BUILD=false
args=()

usage() {
  cat <<'EOF'
Usage: ./bin/run-bitmap-server.sh [--build] [--name NAME] [--host HOST] [--port PORT] [--state SLUG] [--debug-secrets] [--map-tiles N] [--tile-size PX]
       ./bin/run-bitmap-server.sh --account NAME --password PASSWORD [--email EMAIL]

Environment:
  RYLWEEN_DATA_DIR          Authenticated save/log/user data directory. Default: ~/.rylween/data
  RYLWEEN_BITMAP_NAME       Optional named local save to seed/load. Examples: Strong, StrongEndgame
  RYLWEEN_BITMAP_USER       Save/load user id when not using --name.
  RYLWEEN_BITMAP_HOST       Bind host. Default: RYLWEEN_HOST or 127.0.0.1
  RYLWEEN_BITMAP_PORT       Bind port. Default: RYLWEEN_PORT or 8081
  RYLWEEN_BITMAP_STATE      Initial compiled state slug. Default: fresh-replay
  RYLWEEN_BITMAP_LOAD_FILE  Optional shared world save to load.
  RYLWEEN_BITMAP_SAVE_FILE  Optional shared world save path.
  RYLWEEN_BITMAP_MAP_TILES  Wilderness viewport edge length. Default: 6
  RYLWEEN_BITMAP_TILE_SIZE  Wilderness tile size in px. Default: 68
  RYLWEEN_BITMAP_DEBUG_SECRETS Set true to enable hidden manual-test wilderness commands.
  RYLWEEN_BITMAP_BUNDLE     Optional external bundle path. Embedded game is used when omitted.
  RYLWEEN_AUTH_ACCOUNT      Optional account to create/verify before startup.
  RYLWEEN_AUTH_EMAIL        Optional email for RYLWEEN_AUTH_ACCOUNT.
  RYLWEEN_AUTH_PASSWORD     Required when RYLWEEN_AUTH_ACCOUNT is set.
  RYLWEEN_AUTH_SECURE_COOKIES Set true when served through HTTPS.
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
    --build)
      BUILD=true
      ;;
    --name)
      require_next_arg "$arg" "$@"
      RUN_NAME="$1"
      shift
      ;;
    --name=*)
      RUN_NAME="${arg#--name=}"
      ;;
    --user)
      require_next_arg "$arg" "$@"
      RUN_USER="$1"
      shift
      ;;
    --user=*)
      RUN_USER="${arg#--user=}"
      ;;
    --account)
      require_next_arg "$arg" "$@"
      AUTH_ACCOUNT="$1"
      shift
      ;;
    --account=*)
      AUTH_ACCOUNT="${arg#--account=}"
      ;;
    --email)
      require_next_arg "$arg" "$@"
      AUTH_EMAIL="$1"
      shift
      ;;
    --email=*)
      AUTH_EMAIL="${arg#--email=}"
      ;;
    --password)
      require_next_arg "$arg" "$@"
      AUTH_PASSWORD="$1"
      shift
      ;;
    --password=*)
      AUTH_PASSWORD="${arg#--password=}"
      ;;
    --secure-cookies)
      AUTH_SECURE_COOKIES=true
      ;;
    --load)
      require_next_arg "$arg" "$@"
      LOAD_FILE="$1"
      shift
      ;;
    --load=*)
      LOAD_FILE="${arg#--load=}"
      ;;
    --save)
      require_next_arg "$arg" "$@"
      SAVE_FILE="$1"
      shift
      ;;
    --save=*)
      SAVE_FILE="${arg#--save=}"
      ;;
    --title|--no-title)
      args+=("$arg")
      ;;
    --debug-secrets)
      args+=("$arg")
      ;;
    --host)
      require_next_arg "$arg" "$@"
      HOST="$1"
      shift
      ;;
    --host=*)
      HOST="${arg#--host=}"
      ;;
    --port)
      require_next_arg "$arg" "$@"
      PORT="$1"
      shift
      ;;
    --port=*)
      PORT="${arg#--port=}"
      ;;
    --state)
      require_next_arg "$arg" "$@"
      STATE="$1"
      shift
      ;;
    --state=*)
      STATE="${arg#--state=}"
      ;;
    --map-tiles)
      require_next_arg "$arg" "$@"
      MAP_TILES="$1"
      shift
      ;;
    --map-tiles=*)
      MAP_TILES="${arg#--map-tiles=}"
      ;;
    --tile-size)
      require_next_arg "$arg" "$@"
      TILE_SIZE="$1"
      shift
      ;;
    --tile-size=*)
      TILE_SIZE="${arg#--tile-size=}"
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      args+=("$arg")
      ;;
  esac
done

if [[ "$BUILD" == true || ! -x "$LAUNCHER" ]]; then
  (cd "$ROOT_DIR" && ./gradlew :runtime-bitmap-web:installRuntimeBitmapWeb --console=plain)
fi

if [[ -n "$RUN_NAME" ]]; then
  if [[ -n "$RUN_USER" ]]; then
    echo "Use either --name or --user, not both." >&2
    exit 2
  fi
  if [[ -z "$SAVE_FILE" ]]; then
    SAVE_FILE="$DEFAULT_SAVE_FILE"
  fi
  mkdir -p "$DATA_DIR/saves" "$DATA_DIR/logs/runtime-bitmap-web"
  seed_console="$DATA_DIR/logs/runtime-bitmap-web/seed-${RUN_NAME}.console.log"
  RYLWEEN_DATA_DIR="$DATA_DIR" \
    RYLWEEN_SAVE_FILE="$SAVE_FILE" \
    RYLWEEN_LOG_FILE="$DATA_DIR/logs/runtime-bitmap-web/seed-${RUN_NAME}.log" \
    "$ROOT_DIR/bin/run.sh" --build --name "$RUN_NAME" --script X >"$seed_console" 2>&1
  RUN_USER="$RUN_NAME"
  LOAD_FILE="$SAVE_FILE"
fi

if [[ -n "$RUN_USER" ]]; then
  args=("--user" "$RUN_USER" "${args[@]}")
fi

if [[ -n "$AUTH_ACCOUNT" || -n "$AUTH_EMAIL" || -n "$AUTH_PASSWORD" ]]; then
  if [[ -z "$AUTH_ACCOUNT" || -z "$AUTH_PASSWORD" ]]; then
    echo "--account and --password are both required when bootstrapping an account." >&2
    exit 2
  fi
  args=("--account" "$AUTH_ACCOUNT" "--password" "$AUTH_PASSWORD" "${args[@]}")
  if [[ -n "$AUTH_EMAIL" ]]; then
    args=("--email" "$AUTH_EMAIL" "${args[@]}")
  fi
fi

if [[ "$AUTH_SECURE_COOKIES" == "true" ]]; then
  args=("--secure-cookies" "${args[@]}")
fi

if [[ -n "$LOAD_FILE" ]]; then
  args=("--load" "$LOAD_FILE" "${args[@]}")
fi

if [[ -n "$SAVE_FILE" ]]; then
  args=("--save" "$SAVE_FILE" "${args[@]}")
fi

bundle="${RYLWEEN_BITMAP_BUNDLE:-}"
if [[ -n "$bundle" ]]; then
  args=("--bundle" "$bundle" "${args[@]}")
fi

echo "[rylween-bitmap] URL: http://$HOST:$PORT/"
exec "$LAUNCHER" --host "$HOST" --port "$PORT" --data "$DATA_DIR" --state "$STATE" --map-tiles "$MAP_TILES" --tile-size "$TILE_SIZE" "${args[@]}"
