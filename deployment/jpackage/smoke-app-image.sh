#!/usr/bin/env bash
# Starts the packaged app image against a throwaway data directory, waits for /api/v1/ready,
# then checks the web client is served, deep links included. Runs on Linux and on Windows (Git Bash). Needs no root: the data directory and
# port are overridden by command-line arguments, which win over the packaged defaults.
#
#   deployment/jpackage/smoke-app-image.sh [app-image-dir]
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
image="${1:-$here/../../target/jpackage/observatorio-aps}"
port=18080
work="$(mktemp -d)"

if [ -x "$image/bin/observatorio-aps" ]; then
  launcher="$image/bin/observatorio-aps"
else
  launcher="$image/observatorio-aps.exe"
fi

"$launcher" --observatorio.data.directory="$work/data" --server.port="$port" >"$work/app.log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT

# Host must match observatorio.web.allowed-hosts (ENG-49), which knows port 8080 only.
for _ in $(seq 1 90); do
  if curl -fsS -H 'Host: 127.0.0.1:8080' "http://127.0.0.1:$port/api/v1/ready" 2>/dev/null; then
    echo
    echo "smoke: ready"
    for path in / /indicadores/c1-mais-acesso; do
      if ! curl -fsS "http://127.0.0.1:$port$path" | grep -q 'id="root"'; then
        echo "smoke: web client not served at $path" >&2
        exit 1
      fi
    done
    echo "smoke: web client served"
    exit 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

echo "smoke: app image did not become ready" >&2
cat "$work/app.log" >&2
exit 1
