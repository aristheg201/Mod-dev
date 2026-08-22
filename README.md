# Lively NPCs by SVFrame Studio

Two server-side Fabric mods for Minecraft 1.21.1 / Java 21.

- **Lively NPCs by SVFrame Studio** (`livelynpcs`): offline domain-specific living-world and NPC intelligence engine. No external LLM or cloud AI dependency.
- **Lively NPCs: Cobblemon Integration by SVFrame Studio** (`livelynpcs_integration`): requires Lively Core + Cobblemon 1.7.x and provides native Cobblemon Pokemon/NPC bodies, trainer battle AI, Cobblemon world awareness, and optional server-ecosystem adapters.

Current release version: **1.0.0**.

## Architecture rules

1. AI workers only reason over immutable snapshots.
2. AI produces typed proposals; the server-authoritative Authority Layer executes or rejects them.
3. Dialogue is rendered in chat but isolated from normal player chat while a session owns input.
4. Combat reasoning is part of the Lively AI core; game-specific battle state/action mapping belongs in Integration.
5. External integrations must not leak implementation types into the core API.
6. SVF Waypoints is player-facing quest navigation; NPC pathfinding remains Lively's own bounded navigation runtime.

Development branch: `feature/lively-npcs`.
