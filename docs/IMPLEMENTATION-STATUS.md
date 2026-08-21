# Lively implementation status - dev.4

## Verified target stack

- Minecraft 1.21.1 / Java 21 / Fabric Loader >= 0.18.4
- Cobblemon Fabric 1.7.3
- Mega Showdown Fabric 1.8.4 for Cobblemon 1.7.3
- Cobblemon compile-only: `maven.modrinth:MdwFAVRL:kF7CvxTo`

## Core implemented

- offline domain-specific cognition; no external LLM/cloud dependency
- generic `ActorId` / `ActorSnapshot` / `ActorRegistry` for NPCs, players, creatures, factions and system actors
- immutable worker snapshots and atomic mutable NPC state
- traits, needs, beliefs, relationships and bounded memories
- CRC/versioned per-NPC persistence, async preload/save, autosave and shutdown flush
- chat-isolated HYBRID dialogue with local NLU and structured memory updates
- causal quest proposals
- budgeted A* navigation cortex
- combat cortex with depth/beam/node/time budgets and non-biased cheap fallback
- semantic structure registry with bounds, capabilities, points and operational state
- bounded world event engine and story director working on causal tensions/event proposals
- simulation LOD: ACTIVE / NEARBY / DISTANT / DORMANT with staggered cadence
- world-integrity boundary with explicit mutation classes: NONE / TRANSIENT / SEMANTIC / PERSISTENT
- AI-generated block set/break, explosion, fire, fluid, container mutation, NBT mutation, commands and persistent transforms are rejected by policy
- persistent physical transformations require ADMIN source plus explicit registered/allowlisted transform and structure id
- transient effects require safe action type and bounded TTL
- semantic disasters/events do not modify the underlying Minecraft structure

## Cobblemon Integration implemented

- opt-in NPC dialogue using command tag `lively`
- direct Cobblemon 1.7.3 `BattleAI.choose()` backed by Lively Combat Cortex
- NPC battle actor mixin swaps AI only for `lively` / `lively_combat`
- native legal moves/switches validated by Cobblemon
- Mega / Ultra Burst / Z-Move / Dynamax / Terastallization candidates when exposed by `ShowdownMoveset`
- power, accuracy, priority, STAB and type-effectiveness scoring
- matchup-aware switching using visible opponent typing and own known moves
- fair-play boundary against unrevealed player move/item inspection
- shared NPC personality and battle outcome memories

## Verification performed

- Java 21 compilation passed for new actor/world/event/story/LOD/world-integrity core classes
- local dev.4 smoke test confirmed AI persistent block mutation rejection
- local dev.4 smoke test confirmed semantic disaster can exist without changing structure state
- local dev.4 smoke test confirmed story proposal generation and DORMANT LOD classification
- prior persistence round-trip/CRC, A* and combat-timeout regression tests remain in the repository
- added JUnit `WorldIntegrityTest` covering persistent grief actions, semantic disasters, bounded story proposals and LOD
- added GitHub `Lively Loom build` workflow for Java 21, Gradle 8.10.2, tests, `remapJar`, two-JAR verification, SHA256 and artifact upload

## Still incomplete

- persistent world event/history store and migrations
- live Minecraft structure selection/admin commands and capability scanning
- live navigation graph sampling/invalidation/movement application
- social/romance/gossip/reputation simulation
- crime/evidence/investigation engine
- economy/business/faction simulation
- richer setup/status/hazard/ability/known-item battle reasoning
- doubles-aware joint battle planning
- broader fuzz/soak/security tests
