# Content density update — 2026-10-15

This is a source patch and geometry/compiler-stub QA only. It has **not** passed Gradle, Fabric startup, runtime pathfinding, client rendering, PvPvE, or a 20 TPS stress test. Do not install as a production JAR without those checks.

## Changes

- Two existing blueprints now run through `DenseMapDecorator` for distinct public POIs: security checkpoint, quarantine clinic, relay, power substation, freight district, foundry details, service drains, passenger concourse, and maintenance bay. This works only after compilation of the modified source.
- 14 bounded POI zones for ambient navigation and exploration discovery: nine Blackglass and five Redline. Entering a zone once per raid shows a translated (English/Vietnamese) location message; designated high-risk locations increase existing threat by one, up to the current cap of five. Checks run once every 40 ticks per active Operations player and only inspect the map's small immutable POI list.
- Bounded enemy patrol circuits replace the two fixed X-coordinate spawn lines, with per-sector waypoint selection. Path requests are capped at two every five seconds in the current Operations AI pass, and 25 Blackglass + 16 Redline spawn waypoints are checked in the geometry harness. Actual native navigation is still unverified.
- Blackglass Operations now has seven optional, independently discoverable contracts plus an extraction objective. Research-module recovery depends on inspecting research records, while early extraction remains allowed. The server validates interactions at physical blocks; success does not yet award faction currencies.
- 20 physical supply crates on each existing map, matching 20 per-raid deterministic weighted reward definitions; this is still **one rolled item stack per crate**, not a complete tactical loot-container inventory.
- `tools/upgrade_loot.py` appends newly introduced caches into an older server `loot.json` without changing any existing cache definitions, writes a timestamped backup, and operates only on explicit admin invocation. Shut down the server and back up the instance before running.

## Update an existing server configuration

Your existing `config/svextract/loot.json` is NOT overwritten by a mod update. To enable the 10 additional default supply caches, first stop the server, back up the configuration and run:

```sh
python3 tools/upgrade_loot.py --config /path/to/server/config/svextract/loot.json --dry-run
python3 tools/upgrade_loot.py --config /path/to/server/config/svextract/loot.json
```

You may customize the appended items afterward. If the old config is deliberately restricted, do not run this tool; note that newly added physical crates will remain without an associated loot interaction until configured.

## Independent checks executed

- `bash tools/test_core_engines.sh` — 558 rule-engine/contract/patrol smoke scenarios, PASS.
- `bash tools/test_map_density.sh` — compiles actual blueprint source against lightweight stubs and checks bounds, all 20 crate positions, critical objective coordinates, 41/41 candidate patrol-spawn points, POI geometry; NOT an actual Minecraft or Fabric compile.
- Validated language JSON, default loot JSON and explicit migration logic.

## Unfinished / not claimed

This source patch must be compiled against the actual Fabric/Minecraft libraries. The uploaded checkpoint JAR remains unchanged. This update does not turn the disabled Black Zone, Battle Royale, or Apex engines into playable modes; it does not enable shared squads, genuine extractor bots, functioning traders, advanced looting, or the target graphical GUI. Those require additional compiled gameplay integrations and runtime validation. The current 105x105 Blackglass layout also remains a compact test environment, not a full Delta Force Operations-scale district.

## Build blocker

The existing Gradle 9.5.0 wrapper was executed in this environment after this patch. It failed before compilation with `java.net.UnknownHostException: github.com`; no Fabric runtime tests or production-ready JAR were produced. The repository includes a Java 21 GitHub Actions workflow, now checking the map geometry as well; it must be committed and executed in a network-enabled runner.
