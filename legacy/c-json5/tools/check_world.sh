#!/usr/bin/env bash
#
# Validate every JSON5 world file through the game's own loader.
#
# The rylween binary's --check-world flag parses data/world.json5,
# follows every $import to its shard, and runs every fold_* validation
# (schema checks + fatal errors via die()). A clean exit means the full
# tree parsed; any failure prints "res: <path>:<line>: <msg>" to stderr
# and the script propagates the non-zero exit.
#
# Why not jq: the files are JSON5 (comments, trailing commas, unquoted
# keys, single-quote strings), and jq is strict JSON only. Routing
# through our own loader also catches semantic errors jq could never
# see — unknown colour names, class names, SE_* effects, out-of-range
# item/monster ids, and so on.
#
# Usage:
#   tools/check_world.sh          parse + validate the world tree
set -euo pipefail

cd "$(dirname "$0")/.."

BIN=./rylween
if [[ ! -x $BIN ]]; then
    echo "$BIN not built; run: make" >&2
    exit 2
fi

"$BIN" --check-world
