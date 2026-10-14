#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"

APP="${RYLWEEN_DEPLOY_APP:-$ROOT_DIR/runtimes/runtime-bitmap-web/build/install/rylween-bitmap-web/bin/rylween-bitmap-web}"
HOST="${RYLWEEN_DEPLOY_HOST:-lan}"
PORT="${RYLWEEN_DEPLOY_PORT:-8081}"
DATA_DIR="${RYLWEEN_DEPLOY_DATA_DIR:-$HOME/.rylween/deploy}"
LOG_FILE="${RYLWEEN_DEPLOY_LOG_FILE:-/tmp/rylween-bitmap-web-deploy.log}"
MAIN_CLASS="${RYLWEEN_DEPLOY_MAIN_CLASS:-rylween.bitmapweb.RylweenBitmapWebRuntimeMainKt}"

case "${1-}" in
    "") MODE=launcher ;;
    --compose) MODE=compose ;;
    *)
        echo "usage: $0 [--compose]" >&2
        exit 2
        ;;
esac

# Address of the interface carrying the default route, i.e. the one other
# machines on the local network can reach.
detect_lan_host() {
    local host="" iface
    if command -v ip >/dev/null 2>&1; then
        host="$(
            ip -4 route get 1.1.1.1 2>/dev/null |
                awk '{ for (i = 1; i < NF; i++) if ($i == "src") { print $(i + 1); exit } }'
        )"
    fi

    # macOS has no `ip`; ask the routing table for the default interface instead.
    if [ -z "$host" ] && command -v route >/dev/null 2>&1; then
        iface="$(route -n get default 2>/dev/null | awk '$1 == "interface:" { print $2; exit }')"
        if [ -n "$iface" ]; then
            host="$(ifconfig "$iface" 2>/dev/null | awk '$1 == "inet" { print $2; exit }')"
        fi
    fi

    # Only bind automatically to a private (RFC 1918) address, never a public one.
    case "$host" in
        10.*|192.168.*|172.1[6-9].*|172.2[0-9].*|172.3[01].*)
            echo "$host"
            ;;
        *)
            return 1
            ;;
    esac
}

resolve_host() {
    case "$HOST" in
        lan)
            detect_lan_host
            ;;
        *)
            echo "$HOST"
            ;;
    esac
}

is_deployed_server() {
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
    if [ -d /proc/self ]; then
        find_existing_pids_proc
    else
        find_existing_pids_ps
    fi
}

find_existing_pids_proc() {
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
        if is_deployed_server "${args[@]}"; then
            printf '%s\n' "$pid"
        fi
    done
    return 0
}

# Without /proc (macOS), ps joins arguments with single spaces, so match each
# expected argument pair as a space-delimited substring of the command line.
find_existing_pids_ps() {
    local pid command
    while read -r pid command; do
        [ "$pid" != "$$" ] || continue
        case " $command " in *" $MAIN_CLASS "*) ;; *) continue ;; esac
        case " $command " in *" --port $PORT "*) ;; *) continue ;; esac
        case " $command " in *" --data $DATA_DIR "*) ;; *) continue ;; esac
        printf '%s\n' "$pid"
    done < <(ps -axww -o pid= -o command=)
    return 0
}

start_detached() {
    if command -v setsid >/dev/null 2>&1; then
        setsid -f "$@" > "$LOG_FILE" 2>&1
    else
        # macOS has no setsid. nohup ignores SIGHUP so the server outlives the
        # terminal; a non-interactive shell already starts `&` jobs with SIGINT ignored.
        nohup "$@" < /dev/null > "$LOG_FILE" 2>&1 &
    fi
}

stop_existing_server() {
    local pids
    pids="$(find_existing_pids)"
    [ -z "$pids" ] && return 0

    echo "Stopping existing deployed bitmap server: $pids"
    kill $pids

    for _ in {1..30}; do
        pids="$(find_existing_pids)"
        [ -z "$pids" ] && return 0
        sleep 0.2
    done

    echo "Existing server did not exit cleanly; forcing: $pids"
    kill -9 $pids
}

# --compose builds and runs the image with compose instead of the installed
# launcher. It publishes on the same detected LAN address unless
# RYLWEEN_PUBLISH_HOST is set in the shell or in .env; every other setting comes
# from compose.yaml and .env, both in this directory.
if [ "$MODE" = compose ]; then
    cd "$DEPLOY_DIR"
    if [ -z "${RYLWEEN_PUBLISH_HOST-}" ] && ! grep -qE '^[[:space:]]*RYLWEEN_PUBLISH_HOST=' .env 2>/dev/null; then
        RYLWEEN_PUBLISH_HOST="$(resolve_host)" || {
            echo "Could not find a private LAN IPv4 address on the default route." >&2
            echo "Set RYLWEEN_PUBLISH_HOST=127.0.0.1 or a specific bind address." >&2
            exit 2
        }
        export RYLWEEN_PUBLISH_HOST
    fi
    compose=(docker compose)
    command -v docker >/dev/null 2>&1 || compose=(podman compose)
    "${compose[@]}" up --build -d
    echo "Started bitmap compose service on http://$("${compose[@]}" port rylween 8081 2>/dev/null)"
    exit 0
fi

HOST="$(resolve_host)" || {
    echo "Could not find a private LAN IPv4 address on the default route." >&2
    echo "Set RYLWEEN_DEPLOY_HOST=127.0.0.1 or a specific bind address." >&2
    exit 2
}

stop_existing_server

start_detached "$APP" \
    --host "$HOST" \
    --port "$PORT" \
    --data "$DATA_DIR" \
    --state fresh-replay \
    --map-tiles 6 \
    --tile-size 68

echo "Started bitmap server on http://$HOST:$PORT"
echo "Log: $LOG_FILE"
