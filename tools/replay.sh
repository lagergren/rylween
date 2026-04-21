#!/usr/bin/env bash
#
# Scripted-input replay harness for byte-identity regression testing.
#
# Each script is a pair of files under tools/replay/:
#   <name>.in       bytes piped to rylween's stdin (keystrokes + getline input)
#   <name>.expect   expected stdout+stderr bytes, captured on a known-good build
#
# Runs use an isolated HOME so cold-start code paths fire every time and
# persistent state does not leak between invocations.
#
# Usage:
#   tools/replay.sh <name>           run one script, diff vs baseline
#   tools/replay.sh --all            run every script under tools/replay/
#   tools/replay.sh <name> --bless   overwrite baseline with current output
#   tools/replay.sh --all --bless    bless every script
set -euo pipefail

cd "$(dirname "$0")/.."

SCRIPT_DIR=tools/replay
BIN=./rylween
PLAYER="Test User"

target=""
bless=0
all=0
for arg in "$@"; do
    case "$arg" in
        --bless) bless=1 ;;
        --all)   all=1 ;;
        -h|--help)
            sed -n '3,18p' "$0"
            exit 0
            ;;
        -*) echo "unknown flag: $arg" >&2; exit 2 ;;
        *)  target="$arg" ;;
    esac
done

if [[ ! -x $BIN ]]; then
    echo "$BIN not built; run: make" >&2
    exit 2
fi

run_one() {
    local name=$1
    local in="$SCRIPT_DIR/$name.in"
    local expect="$SCRIPT_DIR/$name.expect"
    local envfile="$SCRIPT_DIR/$name.env"

    if [[ ! -f $in ]]; then
        echo "missing input: $in" >&2
        return 2
    fi

    local home out
    home=$(mktemp -d)
    out=$(mktemp)
    # shellcheck disable=SC2064
    trap "rm -rf '$home' '$out'" RETURN

    # Optional sidecar: <name>.env sourced in a subshell so replay-scoped
    # env vars (RYL_FAKE_EPOCH, RYL_FAKE_SEED, ...) do not leak into later
    # runs under --all.
    if [[ -f $envfile ]]; then
        # shellcheck disable=SC1090
        ( set -a; . "$envfile"; HOME="$home" "$BIN" "$PLAYER" < "$in" > "$out" 2>&1 ) || true
    else
        HOME="$home" "$BIN" "$PLAYER" < "$in" > "$out" 2>&1 || true
    fi

    local size
    size=$(wc -c < "$out" | tr -d ' ')

    if (( bless )); then
        cp "$out" "$expect"
        printf 'blessed %-24s %s bytes\n' "$name" "$size"
        return 0
    fi

    if [[ ! -f $expect ]]; then
        echo "no baseline for $name; first run with --bless" >&2
        return 1
    fi

    if cmp -s "$expect" "$out"; then
        printf 'ok     %-24s %s bytes\n' "$name" "$size"
        return 0
    fi

    printf 'FAIL   %-24s %s bytes (expected %s)\n' \
        "$name" "$size" "$(wc -c < "$expect" | tr -d ' ')" >&2
    diff "$expect" "$out" | head -40 >&2 || true
    return 1
}

if (( all )); then
    shopt -s nullglob
    status=0
    for f in "$SCRIPT_DIR"/*.in; do
        run_one "$(basename "$f" .in)" || status=1
    done
    exit "$status"
fi

if [[ -z $target ]]; then
    echo "usage: $0 <name>|--all [--bless]" >&2
    exit 2
fi

run_one "$target"
