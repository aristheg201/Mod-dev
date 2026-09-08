#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

# Upstream CobblemonExtras 1.5.0 was compiled against Cobblemon 1.7.0-SNAPSHOT.
sed -i 's/^cobblemon_version=.*/cobblemon_version=1.8.0+1.21.1/' gradle.properties
sed -i 's/^mod_version=.*/mod_version=1.5.0-cobblemon1.8-port3/' gradle.properties

# Advertise the actual runtime target instead of accepting 1.7.x.
sed -i 's/"cobblemon": ">=1.7.0"/"cobblemon": ">=1.8.0"/' fabric/src/main/resources/fabric.mod.json

# Gradle expand() turns the upstream JSON's \n escapes into literal newlines.
# Double-escape them before expansion so the built fabric.mod.json stays valid JSON.
python3 - <<'PY'
from pathlib import Path
p = Path('fabric/src/main/resources/fabric.mod.json')
s = p.read_text()
s = s.replace(r'Cobblemon needs.\n\n"', r'Cobblemon needs.\\n\\n"')
p.write_text(s)
PY

# Cobblemon 1.8 bundles Fabric Language Kotlin 1.13.6 / Kotlin 2.2.20,
# including kotlinx-coroutines 1.10.2. Upstream Extras shadows Kotlin stdlib
# plus coroutines 1.9.0 into its root jar. That makes Fabric resolve shared
# Kotlin classes from CobblemonExtras and is unsafe. Keep only Extras' own code
# and its unrelated shaded dependencies.
python3 - <<'PY'
from pathlib import Path
p = Path('fabric/build.gradle.kts')
s = p.read_text()
old = 'exclude("architectury.common.json", "com/**/*")'
new = 'exclude("architectury.common.json", "com/**/*", "kotlin/**/*", "kotlinx/coroutines/**/*", "_COROUTINE/**/*", "META-INF/kotlin*", "META-INF/*.kotlin_module")'
if old not in s and new not in s:
    raise SystemExit('Expected shadowJar exclude line not found')
s = s.replace(old, new)
p.write_text(s)
PY
