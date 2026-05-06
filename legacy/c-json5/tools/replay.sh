#!/usr/bin/env bash
#
# Scripted-input replay harness for byte-identity regression testing.
#
# Each script is a pair of files under tools/replay/:
#   <name>.in                bytes piped to rylween's stdin (keystrokes + getline input)
#   <name>.expect            expected stdout+stderr bytes, captured on a known-good build
#   <name>.expect_snapshot   optional: expected RYL_SNAPSHOT dump (state on exit).
#                            If present, the harness sets RYL_SNAPSHOT to a tmpfile
#                            and diffs it against this baseline. --bless rewrites it.
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
    local expect_snap="$SCRIPT_DIR/$name.expect_snapshot"
    local envfile="$SCRIPT_DIR/$name.env"

    if [[ ! -f $in ]]; then
        echo "missing input: $in" >&2
        return 2
    fi

    local home out snap
    home=$(mktemp -d)
    out=$(mktemp)
    snap=$(mktemp)
    # shellcheck disable=SC2064
    trap "rm -rf '$home' '$out' '$snap'" RETURN

    # Optional sidecar: <name>.env sourced in a subshell so replay-scoped
    # env vars (RYL_FAKE_EPOCH, RYL_FAKE_SEED, ...) do not leak into later
    # runs under --all. RYL_SNAPSHOT is always exported so the binary
    # writes a fresh snapshot every run; the comparison vs. the baseline
    # only fires when <name>.expect_snapshot exists.
    #
    # RYL_LOG defaults to `null` here (not in the Makefile gate) so future
    # LOG_W/LOG_E drift can't leak into baselines unless a fixture opts in
    # by setting RYL_LOG=stderr (or file:/tcp:) in its sidecar — symmetric
    # with how RYL_FORCE_*, RYL_FAKE_EPOCH compose. AUDIT (LOG_A) is
    # unaffected: it always writes data/rylween.log regardless of RYL_LOG.
    (
        set -a
        RYL_LOG=null
        if [[ -f $envfile ]]; then
            # shellcheck disable=SC1090
            . "$envfile"
        fi
        RYL_SNAPSHOT="$snap" HOME="$home" "$BIN" "$PLAYER" < "$in" > "$out" 2>&1
    ) || true

    local size
    size=$(wc -c < "$out" | tr -d ' ')

    if (( bless )); then
        cp "$out" "$expect"
        local note=""
        if [[ -f $expect_snap || -s $snap ]]; then
            cp "$snap" "$expect_snap"
            note=' + snapshot'
        fi
        printf 'blessed %-24s %s bytes%s\n' "$name" "$size" "$note"
        return 0
    fi

    if [[ ! -f $expect ]]; then
        echo "no baseline for $name; first run with --bless" >&2
        return 1
    fi

    if ! cmp -s "$expect" "$out"; then
        printf 'FAIL   %-24s %s bytes (expected %s)\n' \
            "$name" "$size" "$(wc -c < "$expect" | tr -d ' ')" >&2
        diff "$expect" "$out" | head -40 >&2 || true
        return 1
    fi

    if [[ -f $expect_snap ]]; then
        if ! cmp -s "$expect_snap" "$snap"; then
            printf 'FAIL   %-24s snapshot drift\n' "$name" >&2
            diff "$expect_snap" "$snap" | head -40 >&2 || true
            return 1
        fi
        printf 'ok     %-24s %s bytes + snapshot\n' "$name" "$size"
        return 0
    fi

    printf 'ok     %-24s %s bytes\n' "$name" "$size"
    return 0
}

if (( all )); then
    # Each script is independent: its own tmp HOME, stdin, and optional
    # env sidecar. Run them in parallel, buffer per-script output, and
    # print the buffers in script order so the summary is deterministic
    # regardless of scheduling. Exit status is the OR of the fan-out.
    shopt -s nullglob
    pids=()
    names=()
    bufs=()
    for f in "$SCRIPT_DIR"/*.in; do
        name=$(basename "$f" .in)
        buf=$(mktemp)
        names+=("$name")
        bufs+=("$buf")
        (run_one "$name") >"$buf" 2>&1 &
        pids+=("$!")
    done

    status=0
    for i in "${!pids[@]}"; do
        wait "${pids[$i]}" || status=1
        cat "${bufs[$i]}"
        rm -f "${bufs[$i]}"
    done
    exit "$status"
fi

if [[ -z $target ]]; then
    echo "usage: $0 <name>|--all [--bless]" >&2
    exit 2
fi

run_one "$target"
