#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/source.tar.gz"
cat "$ROOT"/source.tar.gz.b64.part* | base64 -d > "$OUT"
echo "25387d389ddc3ee0da2663071859a6199b5ceddc428070b4383b0b6ff3ed334f  $OUT" | sha256sum -c -
echo "M2.0 source reconstructed: $OUT"
