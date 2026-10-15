# SVEXTRACT source update — 2026-10-15

## Accurate status

This is an **in-development source update**, not a finished mod, not a verified production JAR, and not proof that every game mode is playable. Preserve backups of the original source and production server. Do not deploy these changes to a production server without completing the full Fabric build and native runtime tests.

## Changes made in this update

- Gradle wrapper now targets official Gradle 9.5.0 **GitHub release** and pins its published SHA-256 checksum. `./gradlew` still requires internet to download the distribution and Fabric/Maven dependencies on a machine with network access.
- GitHub Actions Java 21 build and unit-test workflow; includes dependency-free engine smoke test and attempts `clean test remapJar`. **The workflow has not yet run**; its inclusion is not CI success.
- Extracted a pure server-rule engine for Battle Royale: roster, time-based shrinking safe zones, zone damage schedule, eliminations, and last-team-standing results. **Not yet wired into the Minecraft world, matchmaking, or mode admission.**
- Added pure server-side Versus 5v5 Team Deathmatch and Domination score/respawn/capture rules and integrated those rule modes with the existing Minecraft `MissionRuntime` private Versus path. Search & Destroy continues to use the preexisting `DefusalMatch` engine. All require full Fabric/native testing.
- Added a bounded decision planner for rival Extractor AI; **not connected to native entities**, and not evidence of fully functional rival bots.
- Added a bundled, original stylized Operations lobby background, modified front-end mode selection, and updated English/Vietnamese strings. This is **not** a verified native Minecraft screenshot or a complete Call-of-Duty-style GUI rebuild.
- Replaced three fixed cache rewards with ten default cache positions on both preexisting authored maps and seeded per-raid weighted items, stable between preview and claim, with old six-field loot JSON compatibility. New default positions only apply if `config/svextract/loot.json` does not already exist; do not overwrite customized server configurations automatically.
- Changed instance admission chunk radius from `viewDistance+mapRadius` to a map-bounded radius (at viewDistance 10, Blackglass 841 -> 81 scheduled admission chunks; Redline 625 -> 49). This is a source-level work reduction estimate, **not** a measured MSPT improvement.
- Fixed extraction enemy spawn admission to rotate bounded spawn locations and refrain from consuming pending spawn counts on failures. Behavior requires runtime tests.
- Added unit tests for the rule engines and loot selection. Independent Java smoke tests pass 512 deterministic scenarios.

## Current release-gate status

| Gate | State |
| --- | --- |
| Pure Java smoke suite (`tools/test_core_engines.sh`) | PASS — 512 independent scenarios |
| Language JSON syntax validation | PASS |
| Full Fabric Gradle compile and test | BLOCKED — sandbox cannot resolve GitHub distribution/download host; remote workflow not executed |
| Native Minecraft 1.21.1 client boot | NOT TESTED with these changes |
| Native dedicated server boot | NOT TESTED with these changes |
| Delta Force Operations with real human squads | NOT IMPLEMENTED/VERIFIED |
| Battle Royale gameplay, bots, safe-zone damage in-world | ENGINE ONLY, NOT PLAYABLE |
| 5v5 TDM/Domination in-world | WIRED BUT NOT BUILT/RUNTIME-VERIFIED |
| Apex Incursions | NOT IMPLEMENTED |
| Protected hub NPCs and transactional inventory | Preexisting code; NOT RE-VERIFIED against these changes |
| Actual Point Blank compatible gun integration | Preexisting adapter; NOT RE-VERIFIED against these changes |
| Performance under concurrent real multiplayer load | NOT TESTED |

## Build instructions on a networked GitHub runner or developer machine

1. Install Java 21.
2. Ensure the project includes executable `gradlew` and its wrapper JAR (provided).
3. From `svextract/`, run `bash tools/test_core_engines.sh`.
4. Run `./gradlew --version --no-daemon`, which downloads the SHA-pinned official distribution from GitHub.
5. Run `./gradlew clean test remapJar --no-daemon --stacktrace`.
6. Check all test failures and the remapped artifact before any server install.
7. For integration QA, configure actual mod versions and dependency provisioning documented in `docs/INTEGRATIONS.md` and run real Fabric client and dedicated-server sessions.

The existing `.github/workflows/build.yml` will run these checks on an authorized repository when committed and triggered. It **does not** exist on GitHub until somebody pushes it; this update did not push a branch.

## Essential remaining work

This project needs much more than these changes: complete PvPvE squad mission lifecycle and equipment risk, implemented BR world entities and matchmaking, high-fidelity arena and gameplay maps, functioning true extractor-bot loot/evacuation interactions, protected service NPC regression QA, BEconomy transactions, full class/skill shops, interactive tactical loadout/stash UI, Apex bosses, native client and server QA, performance profiling and stability tests. Do not enable disabled modes merely because a pure Java rules engine exists.
