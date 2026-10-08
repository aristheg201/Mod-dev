#!/usr/bin/env bash
# Run after building once and preparing the isolated runtime dependencies.
# Supply a real DISPLAY (for example with xvfb-run). Never run on a player world.
set -euo pipefail
CWORLD_QA_PROFILE=${1:-full}
if [[ $# -gt 0 ]]; then shift; fi
case "$CWORLD_QA_PROFILE" in
    full) CWORLD_QA_FLAGS=() ;;
    restart|noeconomy) CWORLD_QA_FLAGS=("-Dcworld.qa.${CWORLD_QA_PROFILE}=true") ;;
    *) echo 'Usage: run_production_qa.sh full|restart|noeconomy [Gradle JVM arguments]' >&2; exit 2 ;;
esac
cd "$(dirname "$0")/.."
: "${DISPLAY:?Run with a real display or xvfb-run}"
export LIBGL_ALWAYS_SOFTWARE=${LIBGL_ALWAYS_SOFTWARE:-1}
mkdir -p build/production-qa
CWORLD_QA_SERVER_LOG="build/production-qa/${CWORLD_QA_PROFILE}-server.log"
CWORLD_QA_CLIENT_LOG="build/production-qa/${CWORLD_QA_PROFILE}-client.log"
CWORLD_QA_SERVER_PID=''
trap 'if [[ -n "$CWORLD_QA_SERVER_PID" ]]; then kill "$CWORLD_QA_SERVER_PID" 2>/dev/null || true; fi' EXIT
./gradlew "$@" -Dorg.gradle.jvmargs=-Xmx1G "${CWORLD_QA_FLAGS[@]}" --no-daemon runProductionCWorldServer -x remapJar > "$CWORLD_QA_SERVER_LOG" 2>&1 &
CWORLD_QA_SERVER_PID=$!
for CWORLD_QA_TICK in $(seq 1 240); do
    if rg -q 'Done \(' "$CWORLD_QA_SERVER_LOG"; then break; fi
    if ! kill -0 "$CWORLD_QA_SERVER_PID" 2>/dev/null; then tail -n 60 "$CWORLD_QA_SERVER_LOG"; exit 1; fi
    sleep 1
done
rg -q 'Done \(' "$CWORLD_QA_SERVER_LOG"
./gradlew "$@" -Dorg.gradle.jvmargs=-Xmx1G "${CWORLD_QA_FLAGS[@]}" --no-daemon runProductionCWorldClient -x remapJar > "$CWORLD_QA_CLIENT_LOG" 2>&1
wait "$CWORLD_QA_SERVER_PID"
CWORLD_QA_SERVER_PID=''
if rg -q 'CWORLD_PROD_QA_FAIL' "$CWORLD_QA_SERVER_LOG" "$CWORLD_QA_CLIENT_LOG"; then
    rg 'CWORLD_PROD_QA_FAIL' "$CWORLD_QA_SERVER_LOG" "$CWORLD_QA_CLIENT_LOG"
    exit 1
fi
rg -q 'CWORLD_PROD_QA_FINISHED' "$CWORLD_QA_SERVER_LOG"
rg 'CWORLD_PROD_QA_(PASS|FINISHED)' "$CWORLD_QA_SERVER_LOG"
