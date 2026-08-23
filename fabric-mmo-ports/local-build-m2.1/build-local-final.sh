#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${OUT:-$ROOT/local-final}
VERSION=${VERSION:-2.1.0+local.20260824}
CONFIG_ROOT=${CONFIG_ROOT:-}
rm -rf "$OUT"
mkdir -p "$OUT/jars" "$OUT/stubs-src/net/fabricmc/api" "$OUT/stubs"
cat > "$OUT/stubs-src/net/fabricmc/api/ModInitializer.java" <<'JAVA'
package net.fabricmc.api;
public interface ModInitializer { void onInitialize(); }
JAVA
javac --release 21 -encoding UTF-8 -d "$OUT/stubs" "$OUT/stubs-src/net/fabricmc/api/ModInitializer.java"
compile_module() {
  local key="$1" runtime="$2" artifact="$3"
  local classes="$OUT/$key/classes" stage="$OUT/$key/stage"
  mkdir -p "$classes" "$stage"
  find "$ROOT/reconstructed-runtime-src/common-runtime/src/main/java" "$ROOT/reconstructed-runtime-src/$runtime/src/main/java" "$ROOT/reconstructed-runtime-src/fabric-platform/$key/src/main/java" -name '*.java' -print0 \
    | sort -z | xargs -0 javac --release 21 -encoding UTF-8 -cp "$OUT/stubs" -d "$classes"
  cp -a "$classes/." "$stage/"
  sed 's/${version}/'"$VERSION"'/g' "$ROOT/reconstructed-runtime-src/fabric-platform/$key/src/main/resources/fabric.mod.json" > "$stage/fabric.mod.json"
  if [ "$key" = "mythicmobs-fabric" ]; then
    cp "$ROOT/reconstructed-runtime-src/mythicmobs-runtime/src/main/resources/mythicmobs-core-components.tsv" "$stage/"
  fi
  jar --create --file "$OUT/jars/$artifact" -C "$stage" .
  if jar tf "$OUT/jars/$artifact" | grep -q '^net/fabricmc/api/ModInitializer.class$'; then
    echo "Fabric stub leaked into $artifact" >&2; exit 1
  fi
}
compile_module mythiclib-fabric mythiclib-runtime MythicLib-Fabric-1.7.1-port-local.jar
compile_module mmocore-fabric mmocore-runtime MMOCore-Fabric-1.13.1-port-local.jar
compile_module mmoitems-fabric mmoitems-runtime MMOItems-Fabric-6.10.1-port-local.jar
compile_module mythicmobs-fabric mythicmobs-runtime MythicMobs-Fabric-5.6.2-port-local.jar
CP="$OUT/stubs:$OUT/jars/MythicLib-Fabric-1.7.1-port-local.jar:$OUT/jars/MMOCore-Fabric-1.13.1-port-local.jar:$OUT/jars/MMOItems-Fabric-6.10.1-port-local.jar:$OUT/jars/MythicMobs-Fabric-5.6.2-port-local.jar"
run_gate(){ java -ea -cp "$CP" "$1"; }
run_gate vn.svframe.mythiclibfabric.runtime.MythicLibRuntimeSmoke
run_gate vn.svframe.mythiclibfabric.runtime.session.MythicLibSessionSmoke
run_gate vn.svframe.mythiclibfabric.runtime.passive.MythicLibPassiveTriggerSmoke
run_gate vn.svframe.mmocorefabric.runtime.MMOCoreRuntimeSmoke
run_gate vn.svframe.mmocorefabric.runtime.M1_6Smoke
run_gate vn.svframe.mmocorefabric.runtime.player.MMOCoreClassSwitchSmoke
run_gate vn.svframe.mmoitemsfabric.runtime.MMOItemsRuntimeSmoke
run_gate vn.svframe.mmoitemsfabric.runtime.M1_5Smoke
run_gate vn.svframe.mythicmobsfabric.runtime.MythicMobsRuntimeSmoke
run_gate vn.svframe.mythicmobsfabric.runtime.skills.MythicMobsM20Smoke
(cd "$OUT/jars" && sha256sum *.jar | sort > "$OUT/SHA256SUMS")
cat "$OUT/SHA256SUMS"
