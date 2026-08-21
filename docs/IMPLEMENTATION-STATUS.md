# Lively implementation status - dev.3

## Verified target stack

- Minecraft 1.21.1 / Java 21 / Fabric Loader >= 0.18.4
- Cobblemon Fabric 1.7.3
- Mega Showdown Fabric 1.8.4 for Cobblemon 1.7.3
- Cobblemon compile-only: `maven.modrinth:MdwFAVRL:kF7CvxTo`

The Cobblemon/Mega Showdown versions above come from the current server mod archive. The Maven coordinate is the official Modrinth artifact for Cobblemon 1.7.3 Fabric / Minecraft 1.21.1.

## Core implemented

- offline domain-specific utility cognition; no external LLM/cloud AI dependency
- immutable NPC/world snapshots for worker reasoning
- atomic mutable `NpcState`: traits, needs, structured beliefs, relationships, bounded memories
- versioned per-NPC `.lnpc` persistence with CRC32, size/count bounds and atomic replacement
- async state preload/save registry, periodic autosave and shutdown flush
- bounded prioritized AI scheduler with stale-revision protection
- server-authoritative typed-action validation
- lightweight Vietnamese/English NLU
- chat-isolated HYBRID dialogue with rotating nonce and input throttling
- dialogue writes structured memory/belief/relationship state
- causal quest proposals with validation
- budgeted immutable-graph A* Navigation Cortex
- Combat Cortex with cheap fallback plus depth/beam/node/time budgets
- core regression tests for state corruption, navigation and combat timeout fallback

## Cobblemon Integration implemented

- opt-in NPC dialogue using command tag `lively`
- direct Cobblemon 1.7.3 `BattleAI.choose()` implementation backed by Lively Combat Cortex
- NPC battle actor mixin swaps AI only for NPCs tagged `lively` or `lively_combat`
- native legal move/switch responses validated by Cobblemon
- Mega / Ultra Burst / Z-Move / Dynamax / Terastallization candidates when exposed by `ShowdownMoveset`
- move scoring now includes accuracy, power, priority, STAB and standard type effectiveness
- switch scoring considers reserve HP, offensive type coverage and defensive type risk against currently visible opponents
- unknown/custom elemental types deliberately fall back to neutral rather than inventing effectiveness
- AI does not inspect unrevealed player moves/items merely because the server could access them
- battle aggression/caution uses the same NPC personality traits (`brave`, `greedy`, `suspicious`) as overworld cognition
- every selected battle action is written into memory
- NPC battle `win` / `lose` hooks write high-importance outcome memories with opponent actor IDs
- recent battle outcome balance slightly adjusts future aggression/caution, providing bounded experience adaptation without live neural-network training
- combat remains synchronous but hard-budgeted because Cobblemon requires an immediate AI choice

## Verification performed

- Java 21 compilation passed for game-agnostic core after dev.2 changes
- state persistence round-trip and CRC smoke tests passed
- A* route smoke test passed
- combat timeout regression passed after fixing first-action bias
- Cobblemon battle signatures were verified directly with `javap` against the production-server 1.7.3 JAR
- Gradle wiring targets the official Cobblemon 1.7.3 Modrinth Maven artifact

## Still incomplete

- ability-aware and held-item-aware reasoning using only legitimately known information
- revealed-opponent-move observation and per-opponent tendency model
- doubles-aware joint action / target synergy planning
- richer status/setup/hazard/value modelling
- live Minecraft navigation graph sampling, invalidation, door/jump/water rules and movement application
- full NPC lifecycle unload persistence wiring
- economy, faction and emergent-story simulation
- production config schemas/migrations/admin tooling
- broader fuzz/soak/security regression suite
- full Fabric Loom remap build in this execution environment: there is no Gradle binary/wrapper available and the attempted Gradle distribution download failed; no remap build is falsely claimed
