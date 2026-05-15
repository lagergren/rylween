#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
INSTALL_HOME="$ROOT_DIR/runtime/build/install/rylween"
LAUNCHER="$INSTALL_HOME/bin/rylween"
BUNDLE="$ROOT_DIR/build/compiled/rylween-bundle.json"
DATA_DIR="${RYLWEEN_DATA_DIR:-$HOME/.rylween/data}"
DEFAULT_SAVE_FILE="${RYLWEEN_SAVE_FILE:-$DATA_DIR/saves/world.rsave}"
DEFAULT_LOG_FILE="${RYLWEEN_LOG_FILE:-$DATA_DIR/logs/runtime/latest.log}"
DEFAULT_USER="${RYLWEEN_USER:-local}"
run_name="${RYLWEEN_NAME:-}"

require_next_arg() {
  local option="$1"
  if [[ $# -lt 2 ]]; then
    echo "$option requires a value" >&2
    exit 2
  fi
}

build=false
title_seen=false
bundle_seen=false
user_seen=false
load_seen=false
save_seen=false
log_seen=false
args=()
while [[ $# -gt 0 ]]; do
  arg="$1"
  shift
  case "$arg" in
    --build)
      build=true
      ;;
    --title|--no-title)
      title_seen=true
      args+=("$arg")
      ;;
    --bundle)
      require_next_arg "$arg" "$@"
      bundle_seen=true
      args+=("$arg" "$1")
      shift
      ;;
    --bundle=*)
      bundle_seen=true
      args+=("$arg")
      ;;
    --user)
      require_next_arg "$arg" "$@"
      user_seen=true
      args+=("$arg" "$1")
      shift
      ;;
    --user=*)
      user_seen=true
      args+=("$arg")
      ;;
    --name)
      require_next_arg "$arg" "$@"
      run_name="$1"
      shift
      ;;
    --name=*)
      run_name="${arg#--name=}"
      ;;
    --load)
      require_next_arg "$arg" "$@"
      load_seen=true
      args+=("$arg" "$1")
      shift
      ;;
    --load=*)
      load_seen=true
      args+=("$arg")
      ;;
    --save)
      require_next_arg "$arg" "$@"
      save_seen=true
      args+=("$arg" "$1")
      shift
      ;;
    --save=*)
      save_seen=true
      args+=("$arg")
      ;;
    --log)
      require_next_arg "$arg" "$@"
      log_seen=true
      args+=("$arg" "$1")
      shift
      ;;
    --log=*)
      log_seen=true
      args+=("$arg")
      ;;
    --state|--screen|--script|--seed)
      require_next_arg "$arg" "$@"
      args+=("$arg" "$1")
      shift
      ;;
    --state=*|--screen=*|--script=*|--seed=*)
      args+=("$arg")
      ;;
    --*)
      args+=("$arg")
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Use --name Strong or --name StrongEndgame to load a named save from $DEFAULT_SAVE_FILE." >&2
      exit 2
      ;;
  esac
done

mkdir -p "$(dirname "$DEFAULT_SAVE_FILE")" "$(dirname "$DEFAULT_LOG_FILE")"

if [[ "$build" == true || ! -x "$LAUNCHER" || ! -f "$BUNDLE" ]]; then
  (cd "$ROOT_DIR" && ./gradlew :tools:emitExternalCompiledBundle :runtime:installRuntime --console=plain)
fi

if [[ "$bundle_seen" == false ]]; then
  args=("--bundle" "$BUNDLE" "${args[@]}")
fi

if [[ "$save_seen" == false ]]; then
  args=("--save" "$DEFAULT_SAVE_FILE" "${args[@]}")
fi

if [[ "$log_seen" == false ]]; then
  args=("--log" "$DEFAULT_LOG_FILE" "${args[@]}")
fi

if [[ -n "$run_name" ]]; then
  if [[ "$user_seen" == true ]]; then
    echo "Use either --name or --user, not both." >&2
    exit 2
  fi
  args=("--user" "$run_name" "${args[@]}")
  if [[ "$load_seen" == false ]]; then
    args=("--load" "$DEFAULT_SAVE_FILE" "${args[@]}")
  fi
  if [[ "$title_seen" == false ]]; then
    args=("--no-title" "${args[@]}")
    title_seen=true
  fi
else
  if [[ "$user_seen" == false ]]; then
    args=("--user" "$DEFAULT_USER" "${args[@]}")
  fi
fi

named_state_for_run_name() {
  case "$1" in
    Strong|strong)
      echo "strong-replay"
      ;;
    StrongEndgame|strong-endgame|strong_endgame|Endgame|endgame|Ydarg|ydarg)
      echo "strong-endgame-replay"
      ;;
    *)
      return 1
      ;;
  esac
}

named_party_for_run_name() {
  case "$1" in
    Strong|strong)
      echo "Strong"
      ;;
    StrongEndgame|strong-endgame|strong_endgame|Endgame|endgame|Ydarg|ydarg)
      echo "Strong Endgame"
      ;;
    *)
      return 1
      ;;
  esac
}

normalized_run_user() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9_-]/_/g'
}

ensure_named_save() {
  if [[ -z "$run_name" ]]; then
    return
  fi
  seed_state="$(named_state_for_run_name "$run_name")" || return
  expected_party="$(named_party_for_run_name "$run_name")" || return
  expected_user="$(normalized_run_user "$run_name")"
  bundle_hash="$(sed -n 's/.*"bundleContentHash": "\([^"]*\)".*/\1/p' "$BUNDLE" | head -n 1)"
  save_hash=""
  if [[ -f "$DEFAULT_SAVE_FILE" ]]; then
    save_hash="$(sed -n 's/^bundleHash=//p' "$DEFAULT_SAVE_FILE" | head -n 1)"
  fi
  if [[ -f "$DEFAULT_SAVE_FILE" ]] && grep -q "^user\\.[0-9][0-9]*\\.id=$expected_user$" "$DEFAULT_SAVE_FILE"; then
    if [[ -n "$bundle_hash" && "$save_hash" == "$bundle_hash" ]] &&
      grep -q "^user\\.[0-9][0-9]*\\.party\\.name=$expected_party$" "$DEFAULT_SAVE_FILE"; then
      return
    fi
  fi
  "$LAUNCHER" \
    --bundle "$BUNDLE" \
    --user "$run_name" \
    --state "$seed_state" \
    --save "$DEFAULT_SAVE_FILE" \
    --script SX \
    --no-title \
    --log "$DATA_DIR/logs/runtime/seed-${run_name}.log" \
    >/dev/null
}

ensure_named_save

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
