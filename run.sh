#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
LAUNCHER="$ROOT_DIR/compiled-runtime/build/install/rylween-compiled-runtime/bin/rylween-compiled"
BUNDLE="$ROOT_DIR/build/compiled/rylween-bundle.json"

build=false
args=()
for arg in "$@"; do
  if [[ "$arg" == "--build" ]]; then
    build=true
  else
    args+=("$arg")
  fi
done

if [[ "$build" == true || ! -x "$LAUNCHER" || ! -f "$BUNDLE" ]]; then
  (cd "$ROOT_DIR" && ./gradlew emitExternalCompiledBundle :compiled-runtime:installCompiledRuntime --console=plain)
fi

bundle_seen=false
for arg in "${args[@]}"; do
  if [[ "$arg" == "--bundle" || "$arg" == --bundle=* ]]; then
    bundle_seen=true
  fi
done
if [[ "$bundle_seen" == false ]]; then
  args=("--bundle" "$BUNDLE" "${args[@]}")
fi

exec "$LAUNCHER" "${args[@]}"
