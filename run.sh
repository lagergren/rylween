#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
LAUNCHER="$ROOT_DIR/build/install/rylween-kotlin/bin/rylween"

build=false
args=()
for arg in "$@"; do
  if [[ "$arg" == "--build" ]]; then
    build=true
  else
    args+=("$arg")
  fi
done

if [[ "$build" == true || ! -x "$LAUNCHER" ]]; then
  (cd "$ROOT_DIR" && ./gradlew installKotlinAnsi --console=plain)
fi

exec "$LAUNCHER" "${args[@]}"
