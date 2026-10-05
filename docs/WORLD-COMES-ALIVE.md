# World Comes Alive: content, state and execution

## Reloadable content

A datapack can replace `data/worldcomesalive/living_world/default.json`. It is a complete validated snapshot. Use Minecraft's normal `/reload`; invalid content is logged and the last valid snapshot remains active. Dialogue and route caches are invalidated on successful reload. Already generated buildings and identities remain persistent rather than being regenerated.

The shipped file contains:

- `regions`: biome substring matching, wood/stone palettes, foods, cultural tags and faction identity; put the `*` fallback last.
- `archetypes`: reusable lists of building plots, relative layout coordinates, dimensions, profession slots, bed capacity and semantic markers.
- `professions`: working hours, progression skill, Pokémon species and societal role.
- `firstNames`, `lastNames`, `genderNames`, `traits`: identity generation and configurable personality vectors.
- `actions`: planner preconditions, effects, costs and implemented execution names.
- `dialogue`: topic/profession index keys and activity, trust, memory, season and weather conditions.
- `gifts`: preference tags, professions, need context and base valuation.
- `recipes`: actual input consumption, production, profession and wages.
- `events`: types, durations and daily occurrence probabilities.
- `regionChunks` and `relevance`: generation spacing and entity relevance range, validated to safe bounds.

Copy and adapt the shipped JSON as the schema example. Plot dimensions are 7–21 blocks, beds 0–8 and layouts at most 64 plots. Every plot profession must exist. Semantic marker names are validated against `LivingWorld.Marker`; unknown names reject the reload. New execution names require an implemented server execution handler; JSON alone does not invent a new gameplay action.

## Human presentation

`CitizenModels` supplies separate articulated human meshes: a broader male silhouette with short hair and a narrower female silhouette with shoulder hair and a long tunic. The renderer selects the model from the citizen's persistent gender and synchronizes only its public appearance selector. Children scale down the same appropriate model. No villager outfit or villager render layer is used.

Resource-pack replacements are 64×64 PNGs at `assets/worldcomesalive/textures/entity/citizen/male_0.png` through `male_3.png`, and `female_0.png` through `female_3.png`. The eight original textures are produced by `tools/wca/create_skins.py`. Missing optional textures fall back to the matching vanilla human player skin. Model geometry is code-defined; arbitrary JSON model replacement is not implemented.

## Authority and simulation

The Minecraft server owns citizen identity, households, goals, memory, knowledge, directed relationships, inventory, crowns, Pokémon references, decks, ownership, crimes, events and all interaction outcomes. The client receives a compact view for the current interaction and validates no outcomes itself. C2S requests require a matching expiring session token, snapshot revision, dimension, distance and eligible action; stale or forged requests are rejected.

`WorldSimulation` handles meaningful event wakeups and an adaptive cognitive queue. A due-time queue determines eligibility and a separate priority queue schedules eligible work; urgent interrupts precede background planning. Nonurgent work beyond the budget remains queued. The planner performs bounded cost search over data-defined actions. Local Minecraft navigation executes physical routes; reusable logical road sections carry travel distance, departure, pauses and elapsed progress outside entity relevance. Materialization uses that same logical position and identity. Visible navigation rebases route progress rather than resetting a life.

Settlement construction is a bounded server-thread queue. Layout, semantic records and population are established together, with population derived from real generated beds. Household membership, directed family relationships and profession assignment exist before entity materialization. The settlement registry supplies cached services instead of repeated block scans.

Card Worlds challenges use the repository's existing duel manager, persistent NPC deck and actual server duel UI. NPC-versus-NPC abstract duels use the same engine in a pure worker computation, validated before applying results on the server thread. Minecraft and Cobblemon world/entity mutation stays on the server thread.

## Save files

Authoritative living-world state is written to `<world>/world-comes-alive/world-comes-alive.json`. Checkpoints atomically replace the JSON after flushing the file and keep `.backup` recovery state. Corrupt primary state loads the valid backup; corrupt primary and backup refuse unsafe identity reset. Periodic checkpoints, generation and clean server shutdown save state. Back up this directory with the rest of the world. Card Worlds retains its existing persistence layer as well.

Changing the generation datapack does not silently destroy existing settlements. A fresh QA world is useful when verifying a new layout. The generator rejects wet or excessively uneven sites and does not guarantee that every survey candidate becomes a settlement.

## Reproducing runtime QA

The tasks in `tools/wca/runtime.gradle` launch the remapped production artifact with Fabric API, Cobblemon 1.8.1 and Fabric Language Kotlin 1.13.6. The client task additionally loads a separate QA-only driver; production players never receive that driver.

```sh
./gradlew runWorldComesAliveClient --console=plain
./gradlew runWorldComesAliveServer --console=plain
```

The client needs a real graphical display (Xvfb is sufficient). Its first invocation creates `run-wca/saves/wca-qa` using seed 414212. It moves only the QA player to normal survey chunks; the production chunk-discovery pipeline creates structures, households, citizens and Pokémon. It observes model presentation and routines, submits real interaction packets, gives actual held bread, buys actual stock, starts an NPC duel, moves out of relevance and back, profiles, saves and stops. A second invocation loads the same world, compares every NPC UUID, checks gift memory and transactions, and captures reload evidence. Framebuffer screenshots and machine-readable results go to `qa/`.

Use a clean `run-wca` and archive previous QA outputs before starting another first pass; an existing `expected-identity.json` with a saved world selects the persistence pass. The driver's accessibility onboarding option must be disabled or the normal Minecraft Continue button accepted before the title-screen automation starts. Its player name is within Minecraft's 16-character limit.

Dedicated QA uses a controlled world, `eula=true`, a separate port and local RCON. `tools/wca/rcon_qa.py` forces terrain loading only, observes automatically generated population, measures continuing decisions and tick performance, checkpoints and cleanly stops. Do not expose this isolated offline-mode/RCON configuration publicly. Configure authentication and network policy appropriately for an actual multiplayer server.

The GitHub workflow builds and tests only `feature/world-comes-alive`, then uploads JARs, test reports and the checked-in runtime evidence. Runtime screenshots were captured locally in Minecraft; the workflow does not claim to recreate them.
