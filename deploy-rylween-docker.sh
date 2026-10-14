#!/usr/bin/env bash
# Builds the bitmap web game's image and starts it with docker compose (podman
# compose when docker is missing), published on this machine's private LAN
# address so others on the local network can connect. Same as
# `./deploy-rylween.sh --compose`; settings come from compose.yaml and .env.
set -euo pipefail

if [ "$#" -gt 0 ]; then
    echo "usage: $0" >&2
    exit 2
fi

exec "$(dirname "$0")/deploy-rylween.sh" --compose
