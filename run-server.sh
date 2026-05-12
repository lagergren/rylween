#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"

IMAGE="${RYLWEEN_IMAGE:-rylween:latest}"
CONTAINER_NAME="${RYLWEEN_CONTAINER_NAME:-rylween-web}"
HOST_DATA_DIR="${RYLWEEN_HOST_DATA_DIR:-$HOME/.rylween/data}"
CONTAINER_DATA_DIR="${RYLWEEN_CONTAINER_DATA_DIR:-/data}"
CONTAINER_HOST="${RYLWEEN_CONTAINER_HOST:-0.0.0.0}"
CONTAINER_PORT="${RYLWEEN_CONTAINER_PORT:-${RYLWEEN_PORT:-8080}}"
HOST_PORT="${RYLWEEN_HOST_PORT:-$CONTAINER_PORT}"
PUBLISH_HOST="${RYLWEEN_PUBLISH_HOST:-127.0.0.1}"
BUILD_IMAGE="${RYLWEEN_BUILD:-false}"
RUN_NAME="${RYLWEEN_NAME:-}"
CONTAINER_SAVE_FILE="${RYLWEEN_SAVE_FILE:-$CONTAINER_DATA_DIR/saves/world.rsave}"
HOST_SAVE_FILE=""

log() {
  printf '[rylween-server] %s\n' "$*"
}

usage() {
  cat <<'EOF'
Usage: ./run-server.sh [--build] [--name NAME]

Environment:
  RYLWEEN_IMAGE              Docker image tag. Default: rylween:latest
  RYLWEEN_BUILD              Build image before running when set to true.
  RYLWEEN_NAME               Optional named local save to seed before running. Example: Strong
  RYLWEEN_CONTAINER_NAME     Docker container name. Default: rylween-web
  RYLWEEN_HOST_DATA_DIR      Host data directory. Default: ~/.rylween/data
  RYLWEEN_HOST_PORT          Host port to publish. Default: container port
  RYLWEEN_PUBLISH_HOST       Host interface to publish. Default: 127.0.0.1
  RYLWEEN_CONTAINER_PORT     Container app port. Default: RYLWEEN_PORT or 8080
  RYLWEEN_STATE              Optional initial runtime state slug.
  RYLWEEN_SAVE_FILE          Optional shared world save path inside the container. Default: /data/saves/world.rsave
  RYLWEEN_LOAD_FILE          Optional initial world save path used only when RYLWEEN_SAVE_FILE does not exist.
  RYLWEEN_SEED               Optional runtime RNG seed.
EOF
}

while [[ $# -gt 0 ]]; do
  arg="$1"
  shift
  case "$arg" in
    --build)
      BUILD_IMAGE=true
      ;;
    --name)
      if [[ $# -eq 0 ]]; then
        echo "--name requires a value" >&2
        exit 2
      fi
      RUN_NAME="$1"
      shift
      ;;
    --name=*)
      RUN_NAME="${arg#--name=}"
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

case "$CONTAINER_SAVE_FILE" in
  "$CONTAINER_DATA_DIR"/*)
    HOST_SAVE_FILE="$HOST_DATA_DIR/${CONTAINER_SAVE_FILE#"$CONTAINER_DATA_DIR/"}"
    ;;
esac

mkdir -p "$HOST_DATA_DIR/saves" "$HOST_DATA_DIR/logs"

if [[ "$BUILD_IMAGE" == true ]]; then
  log "building image: $IMAGE"
  log "build context: $ROOT_DIR"
  docker build -t "$IMAGE" "$ROOT_DIR"
fi

if [[ -n "$RUN_NAME" ]]; then
  if [[ -z "$HOST_SAVE_FILE" ]]; then
    echo "Cannot seed --name $RUN_NAME because RYLWEEN_SAVE_FILE is outside $CONTAINER_DATA_DIR." >&2
    exit 2
  fi
  seed_console="$HOST_DATA_DIR/logs/runtime/seed-${RUN_NAME}.console.log"
  mkdir -p "$(dirname "$seed_console")"
  log "seeding named save: $RUN_NAME"
  log "seed host save: $HOST_SAVE_FILE"
  log "seed console log: $seed_console"
  RYLWEEN_DATA_DIR="$HOST_DATA_DIR" \
    RYLWEEN_SAVE_FILE="$HOST_SAVE_FILE" \
    RYLWEEN_LOG_FILE="$HOST_DATA_DIR/logs/runtime/latest.log" \
    "$ROOT_DIR/run.sh" --build --name "$RUN_NAME" --script X >"$seed_console" 2>&1
fi

env_args=(
  -e "RYLWEEN_DATA_DIR=$CONTAINER_DATA_DIR"
  -e "RYLWEEN_HOST=$CONTAINER_HOST"
  -e "RYLWEEN_PORT=$CONTAINER_PORT"
)

forward_env() {
  local name="$1"
  local value="${!name:-}"
  if [[ -n "$value" ]]; then
    env_args+=(-e "$name=$value")
  fi
}

forward_env RYLWEEN_STATE
forward_env RYLWEEN_SAVE_FILE
forward_env RYLWEEN_LOAD_FILE
forward_env RYLWEEN_SEED
forward_env RYLWEEN_BUNDLE

log "image: $IMAGE"
log "container: $CONTAINER_NAME"
log "build before run: $BUILD_IMAGE"
log "host data dir: $HOST_DATA_DIR"
log "container data dir: $CONTAINER_DATA_DIR"
log "volume: $HOST_DATA_DIR:$CONTAINER_DATA_DIR"
log "publish: $PUBLISH_HOST:$HOST_PORT -> $CONTAINER_PORT"
log "container bind host: $CONTAINER_HOST"
log "container data subdirs: $CONTAINER_DATA_DIR/saves and $CONTAINER_DATA_DIR/logs"
log "default shared world save: $CONTAINER_SAVE_FILE"
if [[ -n "$HOST_SAVE_FILE" ]]; then
  log "host shared world save: $HOST_SAVE_FILE"
fi
if [[ -n "$RUN_NAME" ]]; then
  log "seeded name: $RUN_NAME"
  log "browser user name to enter: $RUN_NAME"
fi
for name in RYLWEEN_STATE RYLWEEN_SAVE_FILE RYLWEEN_LOAD_FILE RYLWEEN_SEED RYLWEEN_BUNDLE; do
  value="${!name:-}"
  if [[ -n "$value" ]]; then
    log "forward env: $name=$value"
  fi
done
log "starting docker container"

exec docker run \
  --rm \
  --name "$CONTAINER_NAME" \
  -p "$PUBLISH_HOST:$HOST_PORT:$CONTAINER_PORT" \
  -v "$HOST_DATA_DIR:$CONTAINER_DATA_DIR" \
  "${env_args[@]}" \
  "$IMAGE"
