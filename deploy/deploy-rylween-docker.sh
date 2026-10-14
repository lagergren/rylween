#!/usr/bin/env bash
# Builds the bitmap web game's image and starts it with docker compose (podman
# compose when docker is missing), published on this machine's private LAN
# address so others on the local network can connect. Same as
# `deploy/deploy-rylween.sh --compose`; settings come from deploy/compose.yaml
# and deploy/.env. The root deploy.sh is a symlink to this script.
set -euo pipefail

if [ "$#" -gt 0 ]; then
    echo "usage: $0" >&2
    exit 2
fi

# Follow symlinks such as the root deploy.sh, so the sibling script is found
# next to this file rather than next to the link.
script="$0"
while [ -L "$script" ]; do
    target="$(readlink "$script")"
    case "$target" in
        /*) script="$target" ;;
        *) script="$(dirname "$script")/$target" ;;
    esac
done

exec "$(dirname "$script")/deploy-rylween.sh" --compose
