# Lively architecture baseline

## Two JAR boundary

### Lively NPCs by SVFrame Studio
Owns intelligence: perception contracts, immutable snapshots, goals, utility, planning/search, memory/beliefs, dialogue/NLU, quest proposals, combat cognition, worker scheduling and authority validation.

### Lively NPCs Integration by SVFrame Studio
Owns adapters: Cobblemon entity/battle translation and future bridges for Mega Showdown, LuckPerms, BEconomy, HoloDisplay, Waypoints and claims.

## Thread contract

`main thread -> immutable snapshot -> bounded worker -> typed proposal -> revision check -> authority -> main thread apply`

No worker is allowed to retain or mutate live Minecraft/Cobblemon world/entity objects.

## Security contract

AI output is untrusted. It never executes console commands, arbitrary callbacks or privileged mutations. Every effect must be represented by a registered typed action and validated against the current state/revision.

## Dialogue contract

Dialogue uses vanilla chat as a rendering surface. While a session is active, normal text input is captured server-side and not broadcast. Commands remain commands. Choice clicks carry session id + rotating nonce + choice id; stale/replayed choices are rejected.

## Combat contract

CombatCortex is game-agnostic and performs bounded predictive search over legal actions supplied by an adapter. Hidden information must not be fabricated. Difficulty should change search budget, priors and remembered/inferred knowledge rather than granting omniscience.
