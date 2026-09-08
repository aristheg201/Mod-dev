#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

# Upstream CobblemonExtras 1.5.0 was compiled against Cobblemon 1.7.0-SNAPSHOT.
sed -i 's/^cobblemon_version=.*/cobblemon_version=1.8.0+1.21.1/' gradle.properties
sed -i 's/^mod_version=.*/mod_version=1.5.0-cobblemon1.8-port2/' gradle.properties

# Advertise the actual runtime target instead of accepting 1.7.x.
sed -i 's/"cobblemon": ">=1.7.0"/"cobblemon": ">=1.8.0"/' fabric/src/main/resources/fabric.mod.json

# Cobblemon 1.8 already bundles Fabric Language Kotlin 1.13.6 / Kotlin 2.2.20.
# Never let this sidemod shadow a second copy of kotlin.* into its root jar.
# The server stack trace resolving kotlin.collections.SetsKt from
# CobblemonExtras is exactly the classloading situation we want to eliminate.
python3 - <<'PY'
from pathlib import Path
p = Path('fabric/build.gradle.kts')
s = p.read_text()
old = 'exclude("architectury.common.json", "com/**/*")'
new = 'exclude("architectury.common.json", "com/**/*", "kotlin/**/*", "META-INF/kotlin*", "META-INF/*.kotlin_module")'
if old not in s and new not in s:
    raise SystemExit('Expected shadowJar exclude line not found')
s = s.replace(old, new)
p.write_text(s)
PY
