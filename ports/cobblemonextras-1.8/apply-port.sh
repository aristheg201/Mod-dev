#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

# Upstream 1.5.0 targets Cobblemon 1.7.0-SNAPSHOT. First compile pass is
# intentionally minimal: move the compile/runtime target to Cobblemon 1.8.0
# and let CI expose every real API break before changing behavior.
sed -i 's/^cobblemon_version=.*/cobblemon_version=1.8.0+1.21.1/' gradle.properties
sed -i 's/^mod_version=.*/mod_version=1.5.0-cobblemon1.8-port1/' gradle.properties

# Keep loader/API/Kotlin aligned with the existing 1.21.1 toolchain until a
# concrete 1.8 compile/runtime incompatibility proves that they must change.
