#!/usr/bin/env bash
#
# ASan gate: run the full replay fixture set under ./rylween-asan and
# fail if any run reports an AddressSanitizer / LeakSanitizer marker.
#
# This is deliberately NOT a byte-identity check:
#   * ASan's own output lives on stderr and can interleave into the
#     captured stream in platform-specific ways — scorecard, not golden.
#   * macOS Apple-clang ASan ships without LeakSanitizer, so leak
#     coverage is thinner there than on Linux (§12.1 CI notes).
#
# Each fixture under tools/replay/ runs with an isolated HOME and any
# <name>.env sidecar sourced, identical to tools/replay.sh. Runs fan out
# in parallel and results print in script order.
#
# Usage:
#   tools/replay_asan.sh           run every fixture; exit non-zero on
#                                  any ASan / LSan marker
set -euo pipefail

cd "$(dirname "$0")/.."

SCRIPT_DIR=tools/replay
BIN=./rylween-asan
PLAYER="Test User"

if [[ ! -x $BIN ]]; then
    echo "$BIN not built; run: make asan" >&2
    exit 2
fi

# ASan marker pattern: any mention of Address/Leak sanitizer in the
# combined stdout+stderr capture. Kept loose on purpose — both the
# "==pid==ERROR:" and "SUMMARY:" lines count as failure signals.
MARKER_RE='(AddressSanitizer|LeakSanitizer)'

run_one() {
    local name=$1
    local in="$SCRIPT_DIR/$name.in"
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

    local rc=0
    if [[ -f $envfile ]]; then
        # shellcheck disable=SC1090
        ( set -a; . "$envfile"; HOME="$home" "$BIN" "$PLAYER" < "$in" > "$out" 2>&1 ) || rc=$?
    else
        HOME="$home" "$BIN" "$PLAYER" < "$in" > "$out" 2>&1 || rc=$?
    fi

    local size marker
    size=$(wc -c < "$out" | tr -d ' ')
    marker=$(grep -E "$MARKER_RE" "$out" | head -1 || true)

    if [[ -n $marker ]]; then
        printf 'FAIL   %-24s %s bytes — %s\n' "$name" "$size" "$marker" >&2
        grep -E "$MARKER_RE" "$out" >&2 || true
        return 1
    fi

    if (( rc != 0 )); then
        printf 'FAIL   %-24s %s bytes — exit %d (no ASan marker)\n' "$name" "$size" "$rc" >&2
        return 1
    fi

    printf 'ok     %-24s %s bytes\n' "$name" "$size"
    return 0
}

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
