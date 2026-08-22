# Lively architecture baseline

## Two JAR boundary

### Lively NPCs by SVFrame Studio (`livelynpcs`)
Owns intelligence and living-world simulation: actors, generic command-created NPC bodies/runtime, perception/state contracts, goals, planning, memory/beliefs, social systems, dialogue/NLU, quests, Story Director/world events, semantic structures, navigation, game-agnostic combat cognition, LOD, persistence, runtime configuration, admin tooling and security.

Core is deliberately independent of Cobblemon and optional server ecosystem mods. It must remain usable on a Fabric 1.21.1 dedicated server or integrated singleplayer server without the Cobblemon Integration JAR.

### Lively NPCs: Cobblemon Integration by SVFrame Studio (`livelynpcs_integration`)
This is the Cobblemon extension, not a generic optional bridge pack. Installing it requires both Lively Core and Cobblemon 1.7.x. It owns native Cobblemon Pokemon bodies, native `NPCEntity` trainer bodies, `BattleAI.choose()` integration, battle-visible knowledge, Pokemon/world awareness, migration/research/bond/team signals and Cobblemon-specific quest context.

Optional adapters inside this extension are Mega Showdown, LuckPerms, BEconomy, HoloDisplays, Flan and SVF Waypoints. Absence of those optional mods must fail closed for that adapter only; it must not disable Lively Core or the mandatory Cobblemon functionality of the Integration JAR.

## Core runtime contract

`main thread -> immutable snapshot -> bounded worker -> typed proposal -> revision check -> authority -> main thread apply`

No worker retains or mutates live Minecraft/Cobblemon world/entity objects. Expensive persistence, path planning and cognition work is kept off the server thread; live-world reads and entity mutation remain bounded and server-authoritative.

## Actor contract

Players and NPCs are both actors. Core logic receives `ActorId` / `ActorSnapshot` instead of `if player` special cases. Platform integrations own mutable game entities.

## World integrity contract

AI-generated persistent Minecraft mutation is forbidden. The AI vocabulary may propose dialogue, transient presentation and semantic state changes, but not arbitrary block mutation, explosions, fire, fluids, container edits, NBT edits or commands.

Mutation classes:

- `NONE`: cognition/dialogue only
- `TRANSIENT`: bounded particles/sounds/displays/temporary entities/barriers with TTL
- `SEMANTIC`: Lively state such as structure/event/economy/relationship status
- `PERSISTENT`: physical world mutation; denied to AI and only possible through an admin-authorized allowlisted transform

A story such as a market fire changes history, access, NPC behavior and presentation without destroying the admin's build.

## Persistence contract

NPC definitions, NPC cognitive state, simulation state and world history are versioned and bounded. Hot definition/history writes are asynchronous; definition writes are coalesced, and server shutdown waits on durability barriers instead of leaving pending state in daemon workers. World state remains world-scoped, while runtime policy/configuration remains config-scoped.

## Story/event contract

Story Director detects/ranks tensions and emits bounded `EventProposal` objects. `WorldEventEngine` validates active count, duration, participant/fact sizes and referenced semantic structures before starting an event. Events advance independently from player participation. Pulse cadence, maximum active/new events, enabled categories, tone and AI/social budgets are live runtime configuration rather than hard-coded deployment policy.

## Simulation LOD

- ACTIVE: every tick when relevant/combat/event-near-player
- NEARBY: reduced cadence
- DISTANT: abstract simulation cadence
- DORMANT: very sparse cadence

Stable actor hash offsets spread work across ticks instead of waking every distant NPC together. AI work has bounded queues/backpressure; navigation uses bounded main-thread sampling plus worker A* and never terrain destruction.

## Dialogue contract

Vanilla chat is a rendering surface, not global-chat transport. Dialogue sessions capture normal text, keep commands separate and use rotating nonce protection for clickable choices. Responses use personality, relationships, memory, beliefs, rumors, events, quests and business state; no external/cloud LLM is required.

## Combat contract

Core `CombatCortex` remains game-agnostic. The Cobblemon Integration does not expose a half-bound generic mutation adapter. Native trainer battles are bound directly through Cobblemon `NPCBattleActor` and `BattleAI.choose()`, with legal move/switch/target validation, available battle gimmicks, battle-visible/revealed knowledge, ally-intent coordination and outcome learning.

## Navigation and SVF Waypoints

Lively NPC movement is owned by Core `WorldNavigationService` and does not depend on SVF Waypoints.

SVF Waypoints is an optional **player-facing quest navigation projection** inside the Cobblemon Integration. The 1.2.17 bridge targets its real `SvfWaypoints.instance().navigation()`, `NavigationManager.activate/stop` and `WaypointTarget` contract, uses transient non-server-waypoint targets, and never dispatches arbitrary `/wp` commands.

## Administration contract

Authoring/operations cover NPC create/remove/spawn/despawn/teleport/edit/inspect, skin sources, navigation, schedules, semantic locations/structures and assignment, quest lifecycle/debug, Story Director seeds/arcs/events, performance/AI/path/social diagnostics, integration status and safe live configuration reload. Admin actions remain permission-gated and validated by server authority.
