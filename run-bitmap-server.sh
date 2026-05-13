#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
INSTALL_HOME="$ROOT_DIR/runtime-bitmap-web/build/install/rylween-bitmap-web"
LAUNCHER="$INSTALL_HOME/bin/rylween-bitmap-web"

HOST="${RYLWEEN_BITMAP_HOST:-${RYLWEEN_HOST:-127.0.0.1}}"
PORT="${RYLWEEN_BITMAP_PORT:-${RYLWEEN_PORT:-8081}}"
STATE="${RYLWEEN_BITMAP_STATE:-${RYLWEEN_STATE:-fresh-replay}}"
MAP_TILES="${RYLWEEN_BITMAP_MAP_TILES:-8}"
TILE_SIZE="${RYLWEEN_BITMAP_TILE_SIZE:-64}"
BUILD=false
args=()

usage() {
  cat <<'EOF'
Usage: ./run-bitmap-server.sh [--build] [--host HOST] [--port PORT] [--state SLUG] [--map-tiles N] [--tile-size PX]

Environment:
  RYLWEEN_BITMAP_HOST       Bind host. Default: RYLWEEN_HOST or 127.0.0.1
  RYLWEEN_BITMAP_PORT       Bind port. Default: RYLWEEN_PORT or 8081
  RYLWEEN_BITMAP_STATE      Initial compiled state slug. Default: fresh-replay
  RYLWEEN_BITMAP_MAP_TILES  Wilderness viewport edge length. Default: 8
  RYLWEEN_BITMAP_TILE_SIZE  Wilderness tile size in px. Default: 64
  RYLWEEN_BITMAP_BUNDLE     Optional external bundle path. Embedded game is used when omitted.
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

bundle="${RYLWEEN_BITMAP_BUNDLE:-}"
if [[ -n "$bundle" ]]; then
  args=("--bundle" "$bundle" "${args[@]}")
fi

echo "[rylween-bitmap] URL: http://$HOST:$PORT/"
exec "$LAUNCHER" --host "$HOST" --port "$PORT" --state "$STATE" --map-tiles "$MAP_TILES" --tile-size "$TILE_SIZE" "${args[@]}"
