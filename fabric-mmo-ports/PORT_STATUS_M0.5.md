# Fabric MMO Ports M0.5 status

Target: Minecraft 1.21.1, Java 21, Fabric Loader >= 0.18.4.

## Gates

- Internal Java 21 build: PASS
- Full archived-config corpus regression: PASS
- Mythic current-corpus native PLATFORM lines: 13041 / 13041
- Anti-Mage Knight vertical regression: PASS
- GitHub Actions: intentionally NOT USED
- Loom remap: BLOCKED in the local container because external Gradle/Fabric Maven downloads are unavailable and no Loom cache is preloaded
- Dedicated Fabric server boot: pending Loom/remap gate

## M0.5 additions

### MMOCore-Fabric
- Typed profession and quest definitions instead of raw-map placeholders
- Persistent profession levels/EXP and quest state
- Reusable `exp-sources.yml` registry with recursive `from{source=...}` and cycle guard
- Profession source evaluator covering the original source family, including original `player-placed`, Silk Touch and repair `/100` semantics verified from the 1.13.1 bytecode
- Stateful quest parent/level/profession/cooldown/objective/trigger runtime

### MMOItems-Fabric
- Signed gem socket state with color / `Uncolored` matching and item-type restrictions
- Historic gem payload preservation for upgrade scaling
- Signed custom durability runtime
- Reforge runtime capable of preserving upgrades, gems and durability
- Existing 88 crafting stations / 6640 recipes regression remains green

### MythicMobs-Fabric
- Expanded typed definitions for all 210 persistent spawners and 17 random spawns in the archive
- Persistent spawner timer/warmup/cooldown/cap/player-range/leash runtime
- Spawner runtime is installed into the Fabric server-tick platform path
- Priority-ordered ADD/REPLACE/DENY random-spawn runtime
- `minecraft:*` namespace tolerant world/biome matching
- Fabric `ENTITY_LOAD` integration for random-spawn rules that do not require an exact vanilla SpawnReason
- Exact SpawnReason parity remains a Loom/mixin gate; it is not reported as exact yet

## Corpus snapshot

- MythicLib: 1026 skills, 104 scripts
- MMOCore: 70 classes, 8 professions, 3 quests
- MythicMobs: 4017 skills, 849 mobs, 892 items, 26492 compiled skill lines, 210 spawners, 17 random spawns
- MMOItems: 3277 item templates, 798 ability instances, 468 socketed items, 88 crafting stations, 6640 recipes

## Honesty rule

`100%` is reserved for behavior proven against the original plugin bytecode/API surface. Corpus coverage alone is never treated as full-plugin parity.
