# Map implementation and provenance

Maps are part of the corrective rebuild, not a future menu-only feature. Only SVEXTRACT-owned dimensions/regions may be prepared or restored. Player survival worlds are never reset.

## Blackglass Industrial District

Relative to its isolated slot origin:

| Area | Coordinates | Purpose |
|---|---|---|
| Electrical works | west, (-24,2) to (-5,18) | Optional power terminal; two entrances |
| Research warehouse | east, (12,8) to (35,27) | Investigation and classified core; two entrances |
| Foundry | northwest, (-34,-43) to (-10,-22) | Loading bays, chimneys and exposed gantry |
| Freight lanes | south, z=28..46 | Nine containers with gaps and flanking routes |
| Main streets | x=-5..5 / z=-6..5 | Connected ground routes and three supply caches |
| Service underpass | west x=-42..-39, descending south | Underground anomaly approach |
| Anomaly chamber | centred below district, y=14..60 | Actual optional Vault room; added safe catwalk |
| Portal evacuation | (-19,65,-12) | Free extraction with a defended countdown |
| Freight lift evacuation | (43,65,-35) | Alternate server-authoritative extraction |

The blueprint is original code in `BlackglassBlueprint.java`. The imported underground room is distinct author content. Walkability, cover and sightlines require actual Minecraft inspection; source coordinates alone are not acceptance evidence. Rival squads, locked high-value routes, tuned loot density and the complete extraction-first economy remain unfinished.

## Optional Vault author room

- Repository: https://github.com/Iskallia/Vault-public
- Revision: `a22f15c9cd41ea2e7de66c3d76ff1f4c83dea92b`
- Path: `src/main/resources/data/the_vault/structures/vault/rooms/big1.nbt`
- Direct source: https://raw.githubusercontent.com/Iskallia/Vault-public/a22f15c9cd41ea2e7de66c3d76ff1f4c83dea92b/src/main/resources/data/the_vault/structures/vault/rooms/big1.nbt
- SHA-256: `46b45b7c4572073809499dae39ecb12ff588df9b08492cf1ccf7e8593f6a0f03`
- Actual size: 47 × 47 × 47 blocks.
- Installation: the core JAR ships this small terrain NBT and exports it to `config/svextract/templates/vault_legacy_haven.nbt` when absent. Existing files are preserved and validated against the pinned hash. No author mod JAR is embedded.

This is a legacy public Vault room, not a modern Vault Hunters world, generator, mob pack or complete dungeon port. The importer preserves compatible registered block states, rejects invalid dimensions/palette references, omits entity/block-entity payloads, and removes command blocks, spawners and generation markers. Blackglass adds a safe access path above the room's original lava floor. No arbitrary author commands or uncontrolled mob spawners run.

Compressed input is limited to 2 MiB and decoded NBT to 32 MiB. Blackglass admits templates at most 96 × 48 × 96; generic decoding allows at most 1,000,000 cells. Chunks are prepared with native asynchronous futures, world writes run only on the server thread within `blocksPerTick`, and preparation tickets release on readiness. Admission reacquires residency before deployment; cleanup releases it again. Active mutation records are incremental.

## Skyrim and Azeroth

Skyrim by fonta59: the supplied MediaFire archive is applied to the isolated `svextract:skyrim` hub and its village rendered in the full-pack native client. Use the current author-world navigation and stopped-save installation instructions below. Extraction objective placement and full-map campaign acceptance remain unfinished.

Crafting Azeroth v1.0: the supplied torrent metadata describes approximately 6.98 GB of archive data with no HTTP web seeds. The archive bytes are unavailable in this workspace; no Crafting Azeroth terrain has been applied. Once available, selected bounded regions must be imported into owned instance templates rather than synchronously cloning the entire world.

Monster assets and animations requested from Vault Hunters/Mowzie's Mobs are not included or verified by a terrain import. Compatibility and actual usable assets are separate unfinished work.

## Current author-world navigation (2026-10-15)

The current isolated modular QA save now contains the 164-region Skyrim author import, copied from the prior validated offline import while the QA server was stopped. Original archive SHA-256 is `e8221c8e4eedd05284de8f6ba6d700d9d2e6b3b5f8636c6bc42cdec6bbbf223b`. Only region terrain and the source manifest were transferred; prior test entities/players were excluded. Spawn is (4834,97,4574) in `svextract:skyrim`. This is a protected hub with a real Mission Control NPC, separate from the raid dimensions.

The native lobby now has a Worlds/Hubs submenu. Server state distinguishes installed, preparing and ready worlds; unavailable worlds cannot be entered with an enabled menu button. The existing `/svextract skyrim` command remains available. A full-pack native client entered through Worlds/Hubs, rendered the village and Mission Control, and the server confirmed a stable arrival at (4834,97,4574); see `qa/reports/skyrim-native-arrival-20261015.json`. This accepts the hub arrival, not the full map as mission content. The large author-world data is server save data, not a copied client resource pack or part of the small source archive. The original map URL/importer and manifest remain the installation sources.

The delivered region archives together preserve all 164 regions. Download the complete set into one directory, stop the destination server, back up its save and move any existing `dimensions/svextract/skyrim` directory to a backup location. The installer refuses to overwrite that target, including a blank dimension created by an earlier startup. From the source project run:

```sh
python3 tools/install_terrain_parts.py --save /path/to/server/world --parts-directory /path/to/downloaded/region-parts
```

The Python installer supports Linux/macOS, checks the Minecraft save lock, validates every part and Anvil region, stages the full import and exposes the source manifest only on successful completion. It refuses an existing target and preserves level/player data. The core remains portable Java; Windows administrators can apply all ordinary ZIP directory trees manually only while the server is stopped and backed up. All parts are required before starting. Region archives are server data and do not go into a mods or resourcepacks folder.

The Drive transfer pipeline accepts at most 100 MiB per file. The final terrain delivery therefore contains nine complete region ZIPs (all required), each below that limit. These are server save-data archives, not additional mods. The installer was run against all nine with 164 real regions and preserved existing level/player markers; see `qa/reports/terrain-parts-installer-20261015.json`.
