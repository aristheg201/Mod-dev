# Fabric MMO Ports M0.6 status

Target: Minecraft 1.21.1, Java 21, Fabric Loader >= 0.18.4.

This milestone continues the clean-room parity port of MMOCore 1.13.1, MMOItems 6.10.1, MythicLib 1.7.1 and MythicMobs 5.6.2. It is not declared full parity yet.

## Runtime gates

- `MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS`
- `MMOCORE_SOCIAL_WAYPOINT_LOOT_RUNTIME=PASS`
- `MMOITEMS_INTERACTION_REFORGE_STATE_RUNTIME=PASS`
- `MYTHICMOBS_RANDOMSPAWN_THREAT_FACTION_RUNTIME=PASS`
- Java 21 internal build: PASS
- GitHub Actions: NOT USED

## Legacy configuration compatibility gate

A Java YAML compatibility parser was added for the legacy configuration corpus, including BOM handling, root `{}` / `[]`, indentation-based maps, indentless lists, inline collections and multiline plain scalars used by Mythic mechanics.

Current extracted corpus results:

- YAML files parsed: `1166 / 1166`
- MMOCore class files mapped: `71`
- MMOItems item templates mapped: `2085`
- MythicLib skill definitions mapped: `1043`
- Mythic skill lines parsed: `39260`
- `FULL_LEGACY_CONFIG_MAP=PASS`

The counts above describe the currently extracted archive corpus and are not treated as full-plugin parity percentages.

## M0.6 runtime additions

### MythicLib

- scaling-formula runtime
- per-player cooldown maps
- typed skill metadata
- skill registry / requirement / cast / cooldown pipeline
- existing stat modifier, typed damage, combat pipeline and event bus retained
- legacy skill definition mapper with player scaling parameters and MythicMobs source references

### MMOCore

- skill-tree node graph and parent-level gates
- point consumption and max-level enforcement
- persistent-style player progression model
- skill slot bindings
- friend state
- attribute points
- EXP-level progression hooks
- timed/global source boosters
- legacy class mapper for attributes, skills and skill slots
- existing party, guild, waypoint/Dijkstra and weighted-loot runtimes retained

### MMOItems

- stat definition and compatibility registry
- item template model
- stat build/upgrade scaling runtime
- ability bindings
- legacy item definition mapper including upgrade template/max/success/destroy
- existing socket/gem, durability, reforge and interaction runtimes retained

### MythicMobs

- mob definition and active-mob state
- tags, stance and variable state
- weighted drop-table runtime
- mechanic/condition/targeter registries
- skill execution pipeline
- Mythic mechanic-line parser supporting `mechanic{...} @targeter{...}` syntax and multiline mechanics
- existing RandomSpawn, SpawnReason, threat and faction runtimes retained

## Still required before the final four Fabric JARs can be called complete

- actual Fabric/Yarn platform adapters and mixins for Minecraft 1.21.1 lifecycle, entity/spawn/damage/item/player hooks
- Loom `remapJar` and dedicated Fabric boot gate
- MMOCore full command tree, ScreenHandler GUIs, remaining class/profession/quest/loot chest/economy/friend/social behavior and API/event surfaces
- MMOItems complete stat catalogue behavior, item/NBT-component translation, crafting stations, modifiers, upgrading, consumables/projectiles/listeners, GUI and command surfaces
- MythicLib complete script engine, triggers, placeholders, RPG stat/damage/mitigation/data/profile/sql surfaces and public API compatibility
- MythicMobs complete mechanic/condition/targeter execution catalogue, mob AI/goals, spawners, drops, items, boss bars, variables, auras, timer skills, API and event surfaces

`100%` remains reserved for behavior proven against original bytecode/API plus Fabric boot/runtime gates.
