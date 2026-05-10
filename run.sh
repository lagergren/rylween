#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
INSTALL_HOME="$ROOT_DIR/runtime/build/install/rylween"
LAUNCHER="$INSTALL_HOME/bin/rylween"
BUNDLE="$ROOT_DIR/build/compiled/rylween-bundle.json"

build=false
title_seen=false
args=()
for arg in "$@"; do
  case "$arg" in
    --build)
      build=true
      ;;
    --title|--no-title)
      title_seen=true
      args+=("$arg")
      ;;
    *)
      args+=("$arg")
      ;;
  esac
done

if [[ "$build" == true || ! -x "$LAUNCHER" || ! -f "$BUNDLE" ]]; then
  (cd "$ROOT_DIR" && ./gradlew :tools:emitExternalCompiledBundle :runtime:installRuntime --console=plain)
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

if [[ "$title_seen" == false ]]; then
  args=("--title" "${args[@]}")
fi

RUN_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/rylween-run.XXXXXX")"
cleanup() {
  rm -rf "$RUN_PARENT"
}
trap cleanup EXIT

# Run from a private copy so Gradle rebuilds/tests cannot rewrite jars under
# an active game process and trigger lazy classpath ZipFile failures.
cp -R "$INSTALL_HOME" "$RUN_PARENT/"
RUN_LAUNCHER="$RUN_PARENT/rylween/bin/rylween"

status=0
"$RUN_LAUNCHER" "${args[@]}" || status=$?
exit "$status"
