# Lively implementation status - dev.2

## Verified target stack

- Minecraft 1.21.1 / Java 21 / Fabric Loader >= 0.18.4
- Cobblemon Fabric 1.7.3
- Mega Showdown Fabric 1.8.4 for Cobblemon 1.7.3

The Cobblemon and Mega Showdown versions above were taken from the current server mod archive, not guessed from upstream main. Cobblemon compile-only uses the official Modrinth 1.7.3 Fabric artifact `maven.modrinth:MdwFAVRL:kF7CvxTo`.

## Core implemented

- offline domain-specific utility cognition; no external LLM/cloud AI dependency
- immutable NPC/world snapshots for worker reasoning
- mutable `NpcState` with atomic snapshots, revisioned traits, needs, beliefs, relationships and bounded memories
- versioned per-NPC persistence (`.lnpc`) with CRC32, file-size/string/count bounds and atomic replacement
- async state preload/save registry and periodic server autosave
- bounded prioritized AI scheduler with stale-revision protection
- server-authoritative typed-action validation
- lightweight Vietnamese/English NLU
- chat-isolated HYBRID dialogue sessions with rotating nonce and input throttling
- dialogue -> structured memory/belief/relationship updates
- causal quest proposals with bounded validation
- immutable-graph A* navigation planner with visited-node/time budgets
- game-agnostic Combat Cortex with cheap-score fallback plus bounded depth/beam/node/time search

## Integration implemented

- optional Cobblemon detection
- opt-in Cobblemon NPC dialogue using entity command tag `lively`
- Cobblemon 1.7.3 `BattleAI` implementation backed by Lively Combat Cortex
- NPC battle actor mixin swaps Cobblemon AI only for NPCs tagged `lively` or `lively_combat`
- legal move/switch enumeration and Cobblemon action validation
- Mega / Ultra Burst / Z-Move / Dynamax / Terastallization action candidates when exposed by Cobblemon's `ShowdownMoveset`
- battle aggression/caution incorporates the same Lively NPC personality traits used outside combat
- selected battle decisions are written back into NPC memory
- battle search is synchronous but hard-budgeted because Cobblemon's `BattleAI.choose()` requires an immediate response; no live battle/world object is handed to an async worker
- the old reflective `CombatAdapter` remains read-only; battle mutation now goes through Cobblemon's own `BattleAI.choose()` lifecycle

## Verification performed in this development pass

- Java 21 compilation passed for the game-agnostic dev.2 core
- state persistence round-trip passed
- CRC/versioned state file smoke validation passed
- A* graph search returned the expected route
- combat budget fallback was regression-tested after fixing a real failure where timeout could bias selection toward the first legal action
- Cobblemon 1.7.3 battle signatures were verified directly against the production-server JAR with `javap`: `BattleAI.choose`, `NPCBattleActor`, `MoveActionResponse`, `SwitchActionResponse`, `ShowdownMoveset`, `InBattleMove`, `BattlePokemon`, `BattleSide` and `PokemonBattle.getTurn()`
- Gradle build wiring now targets the official Cobblemon 1.7.3 Modrinth artifact

## Still incomplete

- full type-effectiveness / ability / held-item / revealed-move opponent inference in Cobblemon combat
- doubles-aware paired action planning and richer target synergy
- battle win/loss outcome memory and long-term opponent tendency learning
- live Minecraft block sampling / graph invalidation / movement application for navigation
- persistence unload wiring for every NPC entity lifecycle edge case
- economy, faction and emergent-story simulation
- production config schemas/migrations and admin tooling
- security fuzz/soak/regression suite beyond the new core regression tests
- full Fabric Loom remap build in this execution environment: no Gradle binary/wrapper is currently available here, and downloading the Gradle distribution failed. The code does not claim a remap build that did not happen.
