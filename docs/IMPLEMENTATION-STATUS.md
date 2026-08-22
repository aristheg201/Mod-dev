# Lively implementation status - 0.4.0-rc1

## Target stack

- Minecraft 1.21.1
- Fabric Loader >= 0.18.4
- Java 21
- Fabric API 0.116.12+1.21.1
- Cobblemon Fabric 1.7.3
- Mega Showdown Fabric 1.8.4 for Cobblemon 1.7.3
- server-side primary target; the same common entrypoints support integrated singleplayer servers
- no external LLM/cloud inference dependency

## Architecture

Lively is split into two artifacts.

- `livelynpcs` / **Lively NPCs by SVFrame Studio** owns the living-world AI, persistence, generic NPC runtime, semantic world model and public bridge interfaces.
- `livelynpcs_integration` / **Lively NPCs Integration by SVFrame Studio** owns ecosystem-specific adapters. Core does not import Cobblemon, Mega Showdown, LuckPerms, BEconomy, HoloDisplays, Flan or SVF Waypoints implementation packages.

The authority rule is unchanged: AI proposes; validated server authority executes. AI-generated persistent physical world mutation is rejected by default.

## Core systems implemented and connected

### Cognition and autonomy

- immutable perception/world snapshots with revision checks
- personality traits, needs, beliefs, goals, utility scoring and bounded action search
- outcome learning with recency/importance weighting
- memory importance, confidence, half-life decay, permanent-memory floor and off-thread consolidation
- threat reasoning from observed entities, environment, social hostility/reputation and remembered danger
- ACTIVE / NEARBY / DISTANT / DORMANT simulation LOD
- bounded worker scheduling with main-thread application for Minecraft state

### NPC runtime

- persistent command-created NPC definitions separate from cognitive state
- PLAYER body using the vanilla player model/Fabric FakePlayer
- VANILLA body for supported vanilla entity types
- EXTERNAL body provider used by Cobblemon Pokémon bodies
- spawn/despawn/teleport/look/body replacement/edit flows
- persisted spawned state restoration after server start
- entity UUID to Lively NPC mapping for interaction
- command-only creation; no natural spawn pool or spawn egg registration

### Player-model skins

- Mojang username/profile lookup
- `mojang:<name>` references
- direct skin/image URLs including common skin-page URL resolution paths
- `url:<...>` form
- MineSkin-backed resolution when configured/available
- texture value/signature references
- asynchronous profile resolution with deterministic/default fallback
- brief player-list profile propagation before cleanup so vanilla clients can receive signed texture data

### Navigation

- main-thread bounded walkability sampling
- worker A* search
- cached paths with main-thread validation/invalidation
- follow, escort, flee, schedule travel and group formation movement
- exponential blocked-route backoff; static routes abandon after bounded retries
- semantic destination resolution for structures/home/work points
- no terrain breaking or arbitrary world mutation to make a path

### Social world

- trust, affection, respect, fear, loyalty, attraction, familiarity and scoped reputation
- Stranger / Acquaintance / Friend / Best Friend / Rival / Enemy / Family / Partner / Romance classification
- non-mutating relationship lookups for perception/investigation
- bounded rumor propagation over relationships that actually exist; confidence decays by hop
- romance lifecycle through interest, dating, partnered, engaged, married, separated and ended
- jealousy/stability/shared-memory behavior
- family graph/progression and family-business succession hooks

### Crime and investigation

- murder, theft, assault, missing person, trespassing, fraud and faction crime
- witness/physical/timeline/motive/opportunity/alibi/record/rumor evidence
- suspect ranking from evidence rather than omniscient truth
- truthful, partial, mistaken, deceptive and refused witness statements
- wrong charges can be overturned
- stale/weak investigations can become cold cases
- new evidence reopens cold cases
- semantic investigation state releases structures when a case is resolved/cold
- simulated theft/economic crime never steals from a player's physical inventory

### Economy, business and factions

- virtual wallets, bounded ledger and transaction types
- businesses, staffing, payroll, stock, target stock and demand/supply/scarcity pricing
- autonomous business discovery from NPC metadata, staffing index, restock and hidden/illegal-market access policy
- faction membership, resources, goals, knowledge and inter-faction relations
- bounded faction strategy planning/action runtime for patrol, supply and recruitment behavior

### Schedules, structures and capabilities

- schedules, occupations, home/work semantic destinations and constraint-aware activity selection
- structure registry with world bounds, parent/town membership, named points, capabilities and operational state
- admin selection/structure editing/import-export support
- bounded live capability scanning
- semantic use of chest storage, bed sleep, lectern read/teach, furnace smelt, crafting table craft, bell gather and doors/openables

### Quests and story

- OFFERED / ACTIVE / COMPLETED / FAILED / EXPIRED / CANCELLED quest lifecycle
- delivery, collection, exploration, combat, social, escort, investigation and custom objective types
- runtime signal routing from NPC interaction, investigation interviews, Cobblemon captures/battle wins and spatial arrival
- spatial objectives require entry transitions instead of AFK per-second farming
- player commands for offers/list/info/claim/abandon as a server-side fallback
- emergent Story Director seeds from crime, economy, faction, rumor, ancient-site and Pokémon-migration signals
- explicit story arc phase advancement on event completion; cancelled events abandon their owning arc
- emergent antagonist scoring from simulated actor state rather than `evil=true`
- append-only/history/chronicle persistence and semantic consequences

### Quest waypoint projection

- generic `WaypointBridge` remains in Core
- Integration installs an SVF Waypoints bridge when mod id `svfwaypoints` is present
- active quest objectives resolve to semantic structure entrance, structure center, or explicit world coordinates
- claim/progress/status/join refreshes the projected destination
- completion/cancellation clears or advances to the next locatable objective
- bridge is fail-closed and never dispatches arbitrary `/wp` commands
- reflection discovery is restricted to typed `NavigationManager` method shapes and has an integration test double

## Cobblemon integration

### Pokémon NPC bodies

- uses the real Cobblemon 1.7.3 `PokemonProperties.Companion.parse(...)` API
- creates actual `PokemonEntity` bodies from properties
- Lively Pokémon bodies are persistent, AI-disabled, command-created and tagged `lively` / `lively_body`
- explicit uncatchable property and UNBATTLEABLE tracked flag
- Lively Pokémon bodies are excluded from wild Pokémon migration/world-awareness accounting

### Trainer battle AI

- integrates through Cobblemon `BattleAI.choose()` for opt-in Lively trainers
- validates legal moves, switches and targets through Cobblemon
- Mega Evolution, Ultra Burst, Z-Move, Dynamax and Terastallization candidates when the moveset exposes them
- power, accuracy, priority, STAB, type-effectiveness, HP state, switching and status utility
- observed/revealed move/ability/item knowledge only; unrevealed player information is not read as omniscient state
- multi-active battles use ally-intent/coordination penalties to avoid obvious duplicate/conflicting actions
- battle outcomes feed the same NPC memory/learning system

### Living-world awareness

- capture, evolution, healing, battle victory and wild spawn signals
- Pokémon migration events are derived from bounded spawn windows
- spatial NPC index avoids scanning every NPC for each Cobblemon event
- migration quests receive real semantic coordinates

## Optional ecosystem bridges

Integration contains concrete adapters for:

- LuckPerms permissions
- BEconomy external economy
- HoloDisplays projections
- Flan claim-aware interaction checks
- SVF Waypoints quest navigation

Missing optional mods do not prevent Lively Core from loading.

## Anti-grief / security invariant

AI cannot request arbitrary:

- block set/break/replace
- terrain digging
- explosion, fire or fluid mutation
- redstone/sign/block-entity NBT edits
- player physical container mutation/theft
- command execution
- unregistered persistent transforms

Persistent physical transformation remains an admin-only future/controlled path requiring an allowlisted transform, semantic target and transaction/rollback model. Story disasters, fires, floods and destruction remain semantic plus bounded presentation unless an administrator explicitly invokes a registered transform.

## Persistence and performance

- per-NPC CRC/versioned persistence with atomic replacement, async preload/save and shutdown flush
- durable world-state snapshots/migrations for living-world engines
- append-only world history/chronicle support
- no per-tick all-NPC social or Cobblemon scans
- spatial indexing for high-frequency proximity work
- bounded navigation sampling/search/cache
- bounded rumor, investigation, business and faction pulses
- memory consolidation runs off the Minecraft server thread using only NPC-state locks
- stress/scale regression coverage includes thousands of actors and large social/economy/crime state

## Verification gates

The GitHub `Lively Loom build` workflow validates the release branch with:

1. Core dependency-boundary checks and external/cloud-AI prohibition.
2. Java 21 `clean test remapJar` for both modules.
3. Dedicated Fabric server boot smoke until the server reaches `Done(...)`.
4. Production-layout Cobblemon 1.7.3 server boot smoke using the remapped Lively JARs.
5. Mega Showdown 1.8.4 + Cobblemon 1.7.3 production-layout boot smoke.
6. JAR ZIP/fabric.mod validation and SHA256 output.
7. Artifact upload of both remapped JARs.

Production regression tests cover world-integrity policy, session isolation, persistence/migrations, navigation retry behavior, cognition/memory learning, social gossip, crime progression, generated quest progression, family/business systems and high actor-count scale paths.

## Validation boundary for 0.4.0-rc1

The code scope discussed for Lively is implemented. The remaining release-candidate boundary is deployment validation, not a deliberately missing Lively subsystem:

- The exact SVF Waypoints 1.2.17 binary is not present in this repository/CI runner, so the optional bridge is currently verified by typed discovery tests and fail-closed behavior rather than a real 1.2.17 boot in the Lively workflow.
- A final live-server soak against the complete production modpack/world is still required before calling the artifact `1.0.0` instead of a release candidate. CI already boots the dedicated, Cobblemon and Mega Showdown stacks independently and exercises large simulated state in tests.
