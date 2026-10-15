#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac --release 21 -d "$output" \
  src/main/java/dev/svextract/match/BattleRoyaleMatch.java \
  src/main/java/dev/svextract/match/VersusMatch.java \
  src/main/java/dev/svextract/mission/ExtractorBotBrain.java \
  src/main/java/dev/svextract/mission/RaidPatrols.java \
  src/main/java/dev/svextract/mission/ObjectiveGraph.java \
  src/main/java/dev/svextract/config/WeightedLoot.java \
  tools/CoreEngineSmoke.java
java -ea -cp "$output" CoreEngineSmoke
