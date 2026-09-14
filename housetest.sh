#!/usr/bin/env bash
set -euo pipefail

APP="${RYLWEEN_HOUSETEST_APP:-runtimes/runtime-bitmap-web/build/install/rylween-bitmap-web/bin/rylween-bitmap-web}"
HOST="${RYLWEEN_HOUSETEST_HOST:-sicsapi-vpn}"
PORT="${RYLWEEN_HOUSETEST_PORT:-8081}"
DATA_DIR="${RYLWEEN_HOUSETEST_DATA_DIR:-$HOME/.rylween/house-test}"
LOG_FILE="${RYLWEEN_HOUSETEST_LOG_FILE:-/tmp/rylween-bitmap-web-house-test.log}"
MAIN_CLASS="${RYLWEEN_HOUSETEST_MAIN_CLASS:-rylween.bitmapweb.RylweenBitmapWebRuntimeMainKt}"

detect_sicsapi_vpn_host() {
  local host
  if command -v ip >/dev/null 2>&1; then
    host="$(
      ip -o -4 addr show scope global |
        awk '{ split($4, addr, "/"); if (addr[1] ~ /^10\.11\./) { print addr[1]; exit } }'
    )"
    if [ -n "$host" ]; then
      echo "$host"
      return 0
    fi
  fi

  host="$(
    hostname -I 2>/dev/null |
      tr ' ' '\n' |
      awk '/^10\.11\./ { print; exit }'
  )"
  if [ -n "$host" ]; then
    echo "$host"
    return 0
  fi

  return 1
}

resolve_host() {
  case "$HOST" in
    sicsapi-vpn|vpn|auto-vpn)
      detect_sicsapi_vpn_host
      ;;
    *)
      echo "$HOST"
      ;;
  esac
}

is_house_test_server() {
  local has_main=false has_port=false has_data=false
  while [ "$#" -gt 0 ]; do
    case "$1" in
      "$MAIN_CLASS") has_main=true ;;
      --port) [ "${2-}" != "$PORT" ] || has_port=true ;;
      --data) [ "${2-}" != "$DATA_DIR" ] || has_data=true ;;
    esac
    shift
  done
  [ "$has_main" = true ] && [ "$has_port" = true ] && [ "$has_data" = true ]
}

find_existing_pids() {
  local cmdline pid arg
  local -a args
  for cmdline in /proc/[0-9]*/cmdline; do
    [ -r "$cmdline" ] || continue
    pid="${cmdline#/proc/}"
    pid="${pid%/cmdline}"
    [ "$pid" != "$$" ] || continue
    args=()
    # Preserve argument boundaries, including spaces in data directory names.
    while IFS= read -r -d '' arg; do
      args+=("$arg")
    done < "$cmdline" 2>/dev/null || continue
    if is_house_test_server "${args[@]}"; then
      printf '%s\n' "$pid"
    fi
  done
  return 0
}

stop_existing_server() {
  local pids
  pids="$(find_existing_pids)"
  [ -z "$pids" ] && return 0

  echo "Stopping existing house-test bitmap server: $pids"
  xargs kill <<< "$pids"

  for _ in {1..30}; do
    pids="$(find_existing_pids)"
    [ -z "$pids" ] && return 0
    sleep 0.2
  done

  echo "Existing server did not exit cleanly; forcing: $pids"
  xargs kill -9 <<< "$pids"
}

if [ ! -d /proc/self ] || ! command -v setsid >/dev/null 2>&1; then
  echo "The house-test launcher requires Linux with /proc and setsid." >&2
  exit 2
fi

HOST="$(resolve_host)" || {
  echo "Could not find a local SICSAPI VPN IPv4 address matching 10.11.*." >&2
  echo "Connect the VPN, or set RYLWEEN_HOUSETEST_HOST=127.0.0.1 or a specific bind address." >&2
  exit 2
}

stop_existing_server

setsid -f "$APP" \
  --host "$HOST" \
  --port "$PORT" \
  --data "$DATA_DIR" \
  --state fresh-replay \
  --map-tiles 6 \
  --tile-size 68 \
  > "$LOG_FILE" 2>&1

echo "Started house-test bitmap server on http://$HOST:$PORT"
echo "Log: $LOG_FILE"
