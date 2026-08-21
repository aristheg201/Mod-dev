# Lively architecture baseline

## Two JAR boundary

### Lively NPCs by SVFrame Studio
Owns intelligence and living-world simulation: actors, perception/state contracts, goals, planning, memory/beliefs, social hooks, dialogue/NLU, quests, story/event proposals, semantic structures, navigation, combat cognition, LOD, persistence and security.

### Lively NPCs Integration by SVFrame Studio
Owns adapters: Cobblemon entity/battle translation and future bridges for Mega Showdown, LuckPerms, BEconomy, HoloDisplay, Waypoints and claims.

## Core runtime contract

`main thread -> immutable snapshot -> bounded worker -> typed proposal -> revision check -> authority -> main thread apply`

No worker retains or mutates live Minecraft/Cobblemon world/entity objects.

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

## Story/event contract

Story Director detects/ranks tensions and emits bounded `EventProposal` objects. `WorldEventEngine` validates active count, duration, participant/fact sizes and referenced semantic structures before starting an event. Events advance independently from player participation.

## Simulation LOD

- ACTIVE: every tick when relevant/combat/event-near-player
- NEARBY: reduced cadence
- DISTANT: abstract simulation cadence
- DORMANT: very sparse cadence

Stable actor hash offsets spread work across ticks instead of waking every distant NPC together.

## Dialogue contract

Vanilla chat is a rendering surface, not global-chat transport. Dialogue sessions capture normal text, keep commands separate and use rotating nonce protection for clickable choices.

## Combat contract

Combat Cortex is game-agnostic. Cobblemon Integration binds it through native `BattleAI.choose()`. Difficulty changes search budget/priors/legitimately learned information, not omniscience.
