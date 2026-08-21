# Lively NPCs by SVFrame Studio

Two server-side Fabric mods for Minecraft 1.21.1 / Java 21.

- **Lively NPCs**: offline domain-specific NPC intelligence engine. No external LLM or cloud AI dependency.
- **Lively NPCs Integration**: adapters for Cobblemon and the surrounding server ecosystem.

## Architecture rules

1. AI workers only reason over immutable snapshots.
2. AI produces typed proposals; the server-authoritative Authority Layer executes or rejects them.
3. Dialogue is rendered in chat but isolated from normal player chat while a session owns input.
4. Combat reasoning is part of the Lively AI core; game-specific battle state/action mapping belongs in Integration.
5. External integrations must not leak implementation types into the core API.

Development branch: `feature/lively-npcs`.
