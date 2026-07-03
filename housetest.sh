#!/usr/bin/env bash
set -euo pipefail

APP="${RYLWEEN_HOUSETEST_APP:-runtimes/runtime-bitmap-web/build/install/rylween-bitmap-web/bin/rylween-bitmap-web}"
HOST="${RYLWEEN_HOUSETEST_HOST:-sicsapi-vpn}"
PORT="${RYLWEEN_HOUSETEST_PORT:-8081}"
DATA_DIR="${RYLWEEN_HOUSETEST_DATA_DIR:-/home/marcus/.rylween/house-test}"
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

find_existing_pids() {
  ps -Ao pid=,args= |
    awk -v self="$$" -v main="$MAIN_CLASS" -v port="$PORT" -v data="$DATA_DIR" '
      $1 != self && index($0, main) && index($0, "--port " port) && index($0, "--data " data) {
        print $1
      }
    '
}

stop_existing_server() {
  local pids
  pids="$(find_existing_pids)"
  [ -z "$pids" ] && return 0

  echo "Stopping existing house-test bitmap server: $pids"
  kill $pids

  for _ in {1..30}; do
    pids="$(find_existing_pids)"
    [ -z "$pids" ] && return 0
    sleep 0.2
  done

  echo "Existing server did not exit cleanly; forcing: $pids"
  kill -9 $pids
}

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
