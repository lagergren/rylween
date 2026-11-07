#!/usr/bin/env bash
# Rebuilds the bitmap web game's image and (re)starts it with docker compose
# (podman compose when docker is missing), published on this machine's private
# LAN address so others on the local network can connect, then waits until the
# server answers and prints its address. Settings come from compose.yaml and
# .env next to this script. The repository root's deploy.sh is a symlink to
# this script, so `./deploy.sh` there and `./deploy-rylween.sh` here are the
# same command, and either works from any directory.
#
# --launcher skips Docker and starts the installed bitmap launcher in the
# background instead. --compose names the default explicitly. --https runs the
# released image on the internet behind Caddy and HTTPS instead, with
# compose.https.yaml; deploy/README.md explains what that needs.
set -euo pipefail

# Follow symlinks such as the root deploy.sh, so DEPLOY_DIR is the directory
# holding this script and compose.yaml rather than the one holding the link.
script="$0"
while [ -L "$script" ]; do
    target="$(readlink "$script")"
    case "$target" in
        /*) script="$target" ;;
        *) script="$(dirname "$script")/$target" ;;
    esac
done
DEPLOY_DIR="$(cd "$(dirname "$script")" && pwd)"
ROOT_DIR="$(dirname "$DEPLOY_DIR")"

APP="${RYLWEEN_DEPLOY_APP:-$ROOT_DIR/runtimes/runtime-bitmap-web/build/install/rylween-bitmap-web/bin/rylween-bitmap-web}"
HOST="${RYLWEEN_DEPLOY_HOST:-lan}"
PORT="${RYLWEEN_DEPLOY_PORT:-8081}"
DATA_DIR="${RYLWEEN_DEPLOY_DATA_DIR:-$HOME/.rylween/deploy}"
LOG_FILE="${RYLWEEN_DEPLOY_LOG_FILE:-/tmp/rylween-bitmap-web-deploy.log}"
MAIN_CLASS="${RYLWEEN_DEPLOY_MAIN_CLASS:-rylween.bitmapweb.RylweenBitmapWebRuntimeMainKt}"

# At most one argument, the mode; compose is the default.
case "$#:${1-}" in
    0: | 1:--compose) MODE=compose ;;
    1:--launcher) MODE=launcher ;;
    1:--https) MODE=https ;;
    *)
        echo "usage: $0 [--compose | --launcher | --https]" >&2
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

# Waits up to a minute, or $2 seconds, for any HTTP answer from the URL in $1;
# /api/status answers 401 without a login, which is enough. An HTTPS URL counts
# only once its certificate checks out. `compose up -d` returns as soon
# as the container starts, seconds before the server in it listens, and the
# container port forwarder may accept connections before then, so only an HTTP
# response shows the server is up. Without curl it cannot tell and does not wait.
wait_for_http() {
    local url="$1" deadline=$((SECONDS + ${2:-60}))
    command -v curl >/dev/null 2>&1 || return 0
    until curl --noproxy '*' --silent --output /dev/null --max-time 2 "$url"; do
        [ "$SECONDS" -lt "$deadline" ] || return 1
        sleep 1
    done
}

# HTTPS mode runs compose.https.yaml: the released image, behind Caddy, which
# gets a certificate for RYLWEEN_DOMAIN from Let's Encrypt. It waits until the
# site answers over HTTPS, which includes the time the first certificate takes.
if [ "$MODE" = https ]; then
    cd "$DEPLOY_DIR"
    domain="${RYLWEEN_DOMAIN-}"
    if [ -z "$domain" ] && [ -f .env ]; then
        domain="$(sed -n 's/^[[:space:]]*RYLWEEN_DOMAIN=//p' .env | tail -n 1 | tr -d "\"' ")"
    fi
    if [ -z "$domain" ]; then
        echo "Set RYLWEEN_DOMAIN, the name players open, in $DEPLOY_DIR/.env or the shell." >&2
        echo "$DEPLOY_DIR/README.md explains what the name needs." >&2
        exit 2
    fi
    engine=(docker compose)
    command -v docker >/dev/null 2>&1 || engine=(podman compose)
    compose=("${engine[@]}" -f "$DEPLOY_DIR/compose.https.yaml")
    "${compose[@]}" pull
    "${compose[@]}" up -d

    printf -v manage '%s -f %q' "${engine[*]}" "$DEPLOY_DIR/compose.https.yaml"
    if ! wait_for_http "https://$domain/healthz" 180; then
        echo "https://$domain/ did not answer within 3 minutes. Check that DNS for $domain" >&2
        echo "points at this host and that ports 80 and 443 reach it. Caddy's log says why:" >&2
        echo "  $manage logs caddy" >&2
        exit 1
    fi
    echo "Rylween is up on https://$domain/"
    echo "  Sysop password: $manage exec rylween cat /data/auth/admin-password.txt"
    echo "  Server log:     $manage logs -f rylween"
    echo "  Proxy log:      $manage logs -f caddy"
    echo "  Stop:           $manage down"
    exit 0
fi

# Compose mode builds and runs the image instead of the installed launcher. It
# publishes on the detected LAN address unless RYLWEEN_PUBLISH_HOST is set in
# the shell or in .env; every other setting comes from compose.yaml and .env,
# both in this directory, which is why it runs compose from here.
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

    # The follow-up commands name the compose file by its full path, so they
    # work from any directory, not only from this one or the repository root.
    printf -v manage '%s -f %q' "${compose[*]}" "$DEPLOY_DIR/compose.yaml"
    address="$("${compose[@]}" port rylween 8081 2>/dev/null)" || address=""
    if [ -z "$address" ]; then
        echo "Compose reports no published port for the rylween service; see its log:" >&2
        echo "  $manage logs rylween" >&2
        exit 1
    fi
    if ! wait_for_http "http://$address/api/status"; then
        echo "The server on http://$address/ did not answer within a minute; see its log:" >&2
        echo "  $manage logs rylween" >&2
        exit 1
    fi
    echo "Rylween is up on http://$address/"
    echo "  Sysop password: $manage exec rylween cat /data/auth/admin-password.txt"
    echo "  Server log:     $manage logs -f rylween"
    echo "  Stop:           $manage down"
    exit 0
fi

# --launcher: run the installed bitmap launcher in the background on the LAN
# address, with its own data directory, replacing a server it started earlier.
# Starting it detached hides a missing launcher, so check for it first.
if [ ! -x "$APP" ]; then
    echo "No installed bitmap launcher at $APP." >&2
    echo "Build it with ./gradlew :runtime-bitmap-web:installRuntimeBitmapWeb first." >&2
    exit 2
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
