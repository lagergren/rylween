#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

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
AUTH_ACCOUNT="${RYLWEEN_AUTH_ACCOUNT:-}"
AUTH_EMAIL="${RYLWEEN_AUTH_EMAIL:-}"
AUTH_PASSWORD="${RYLWEEN_AUTH_PASSWORD:-}"
AUTH_SECURE_COOKIES="${RYLWEEN_AUTH_SECURE_COOKIES:-}"
CONTAINER_SAVE_FILE="${RYLWEEN_SAVE_FILE:-$CONTAINER_DATA_DIR/saves/world.rsave}"
HOST_SAVE_FILE=""

log() {
  printf '[rylween-server] %s\n' "$*"
}

usage() {
  cat <<'EOF'
Usage: ./bin/run-server.sh [--build] [--name NAME]
       ./bin/run-server.sh --account NAME --password PASSWORD [--email EMAIL]

Environment:
  RYLWEEN_IMAGE              Docker image tag. Default: rylween:latest
  RYLWEEN_BUILD              Build image before running when set to true.
  RYLWEEN_NAME               Optional named local save to seed before running. Examples: Strong, StrongEndgame
  RYLWEEN_CONTAINER_NAME     Docker container name. Default: rylween-web
  RYLWEEN_HOST_DATA_DIR      Host data directory. Default: ~/.rylween/data
  RYLWEEN_HOST_PORT          Host port to publish. Default: container port
  RYLWEEN_PUBLISH_HOST       Host interface to publish. Default: 127.0.0.1
  RYLWEEN_CONTAINER_PORT     Container app port. Default: RYLWEEN_PORT or 8080
  RYLWEEN_STATE              Optional initial runtime state slug.
  RYLWEEN_SAVE_FILE          Optional shared world save path inside the container. Default: /data/saves/world.rsave
  RYLWEEN_LOAD_FILE          Optional initial world save path used only when RYLWEEN_SAVE_FILE does not exist.
  RYLWEEN_AUTH_ACCOUNT       Optional account to create/verify before startup.
  RYLWEEN_AUTH_EMAIL         Optional email for RYLWEEN_AUTH_ACCOUNT.
  RYLWEEN_AUTH_PASSWORD      Required when RYLWEEN_AUTH_ACCOUNT is set.
  RYLWEEN_AUTH_SECURE_COOKIES Set true when served through HTTPS.
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
    --account)
      if [[ $# -eq 0 ]]; then
        echo "--account requires a value" >&2
        exit 2
      fi
      AUTH_ACCOUNT="$1"
      shift
      ;;
    --account=*)
      AUTH_ACCOUNT="${arg#--account=}"
      ;;
    --email)
      if [[ $# -eq 0 ]]; then
        echo "--email requires a value" >&2
        exit 2
      fi
      AUTH_EMAIL="$1"
      shift
      ;;
    --email=*)
      AUTH_EMAIL="${arg#--email=}"
      ;;
    --password)
      if [[ $# -eq 0 ]]; then
        echo "--password requires a value" >&2
        exit 2
      fi
      AUTH_PASSWORD="$1"
      shift
      ;;
    --password=*)
      AUTH_PASSWORD="${arg#--password=}"
      ;;
    --secure-cookies)
      AUTH_SECURE_COOKIES=true
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
  docker build -f "$ROOT_DIR/deploy/docker/Dockerfile" -t "$IMAGE" "$ROOT_DIR"
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
    "$ROOT_DIR/bin/run.sh" --build --name "$RUN_NAME" --script X >"$seed_console" 2>&1
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
forward_env RYLWEEN_AUTH_DEV_USER
forward_env RYLWEEN_ADMIN_ACCOUNT
forward_env RYLWEEN_ADMIN_PASSWORD
forward_env RYLWEEN_VICTIM_ACCOUNT

if [[ -n "$AUTH_ACCOUNT" || -n "$AUTH_EMAIL" || -n "$AUTH_PASSWORD" ]]; then
  if [[ -z "$AUTH_ACCOUNT" || -z "$AUTH_PASSWORD" ]]; then
    echo "--account and --password are both required when bootstrapping an account." >&2
    exit 2
  fi
  env_args+=(-e "RYLWEEN_AUTH_ACCOUNT=$AUTH_ACCOUNT")
  env_args+=(-e "RYLWEEN_AUTH_PASSWORD=$AUTH_PASSWORD")
  if [[ -n "$AUTH_EMAIL" ]]; then
    env_args+=(-e "RYLWEEN_AUTH_EMAIL=$AUTH_EMAIL")
  fi
fi

if [[ "$AUTH_SECURE_COOKIES" == "true" ]]; then
  env_args+=(-e "RYLWEEN_AUTH_SECURE_COOKIES=true")
fi

if [[ -n "$RUN_NAME" && -z "${RYLWEEN_AUTH_DEV_USER:-}" ]]; then
  env_args+=(-e "RYLWEEN_AUTH_DEV_USER=$RUN_NAME")
fi

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
  log "dev auth bypass user: $RUN_NAME"
fi
for name in RYLWEEN_STATE RYLWEEN_SAVE_FILE RYLWEEN_LOAD_FILE RYLWEEN_SEED RYLWEEN_BUNDLE RYLWEEN_AUTH_DEV_USER RYLWEEN_AUTH_ACCOUNT RYLWEEN_AUTH_EMAIL RYLWEEN_AUTH_SECURE_COOKIES RYLWEEN_ADMIN_ACCOUNT RYLWEEN_VICTIM_ACCOUNT; do
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
