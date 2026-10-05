# REJECTED V1 PROTOTYPE — NEGATIVE BASELINE

The furnishing, settlement morphology, agricultural footprint and terrain integration in this prototype are explicitly rejected. These screenshots and historical observations are preserved for comparison, not as acceptance evidence for Generation Engine V2. The later prototype run crashed in agricultural chunk synchronization; fixes need new runtime verification. V2 QA must use fresh generated worlds and separate versioned evidence.

# World Comes Alive — runtime QA

Repository: `aristheg201/Mod-dev`  
Branch: `feature/world-comes-alive`  
Platform: Minecraft 1.21.1, Fabric Loader 0.18.4, Java 21, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.1, Fabric Language Kotlin 1.13.6.

## Scope and provenance

This report covers the implemented playable living-settlement slice. It does **not** certify every system in the full 50-part product specification as complete. Original male and female human meshes and medieval player-style skins replace villager presentation.

The client evidence comes from Minecraft running the remapped production JAR with a separately packaged QA driver on a real OpenGL framebuffer under Xvfb, at 1280×720. Screenshots are saved with Minecraft's `ScreenshotRecorder`. The driver moves only the player, submits normal interaction packets and supplies the held gift item. It never places NPCs, Pokémon, buildings, roads or ownership claims. Production terrain-discovery generation and simulation perform those actions.

A fresh default Overworld uses seed `414212`. Exploration finds two 12-resident hamlets and the 24-resident, ten-building village Winterford: 48 persistent NPCs altogether. Homes, jobs, household relationships, companion references, schedules, stock and persistent decks are generated automatically from the layout and residential capacity. No administrator town construction or NPC assignment is required.

## Evidence map

| Requirement | Actual runtime artifact | Supporting evidence |
| --- | --- | --- |
| Fresh automatic settlement and medieval structures | [worldgen-settlement-overview.png](worldgen-settlement-overview.png) | `WCA_GENERATED` / `WCA_BOOTSTRAP_READY`, `manualSetup=false` in client log |
| Coherent town, paths and multiple occupied buildings | [generated-town-road-layout.png](generated-town-road-layout.png) | Ten registered plots, ten households, 24 residents in target village |
| Generated citizens physically present | [npc-population.png](npc-population.png) | Materialization log records persistent UUIDs and real positions |
| Original male human model | [npc-male-model.png](npc-male-model.png) | `WCA_QA_HUMAN_MODEL` confirms male appearance on the real client |
| Original female human model | [npc-female-model.png](npc-female-model.png) | `WCA_QA_HUMAN_MODEL` confirms female appearance on the real client |
| Household / generated home association | [npc-home-routine.png](npc-home-routine.png) | `WCA_QA_HOME`: home, household and actual position |
| Scheduled work / profession location | [npc-work-routine.png](npc-work-routine.png) | `WCA_QA_WORK`: actual working activity and workplace |
| Visible Cobblemon civilian integration | [cobblemon-npc-integration.png](cobblemon-npc-integration.png) | Real Wooloo partner entity; NPC partner UUID/species/animal-care role |
| Contextual dialogue and interaction UI | [contextual-dialogue.png](contextual-dialogue.png) | Actual UseEntity interaction opens `CitizenScreen`; server-selected content |
| Gift response and relationship progression | [relationship-interaction.png](relationship-interaction.png) | Actual held bread consumed, gift memory written, friendship changes |
| Functional commerce | [economic-interaction.png](economic-interaction.png) | Stock and crowns exchanged, actual bread item delivered, transaction counter advances |
| NPC Card Worlds challenge | [card-world-challenge.png](card-world-challenge.png) | Appropriate persistent NPC offers challenge option |
| Successfully initiated NPC duel | [card-world-duel-runtime.png](card-world-duel-runtime.png) | Existing authoritative Card Worlds engine, NPC name and persistent 40-card deck |
| Full → abstract → full simulation | [client-runtime.log](client-runtime.log), [performance-runtime.json](performance-runtime.json) | `WCA_QA_ABSTRACT_CONFIRMED`: decisions continue while absent; counters prove dematerialization and rematerialization |
| Actual save / server restart | [persistence-after-reload.png](persistence-after-reload.png), [persistence-runtime.json](persistence-runtime.json) | Exact 48-UUID set, household assignment, gift memory and transactions survive restart |
| Meaningful client-world population performance | [performance-runtime.png](performance-runtime.png), [performance-runtime.json](performance-runtime.json) | Runtime TPS and server tick cost, full/logical population, planning and transaction counts |
| Headless dedicated-server compatibility | [dedicated-server-runtime.json](dedicated-server-runtime.json), [dedicated-server-runtime.log](dedicated-server-runtime.log) | Real dedicated server, 24 unseen citizens, continuing decisions and transactions, built-in `tick query` |
| Automated coverage | [automated-tests.json](automated-tests.json), [build-verification.log](build-verification.log) | Full repository suite including the living-world cases |
| Exact built artifact | [build-artifacts.json](build-artifacts.json) | Production JAR size and SHA-256; isolated QA JAR identified separately |

## Automated checks

The living-world suite covers deterministic generation across seeds, capacity bounds, households and directed family roles, professions, configurable schedules, persistent gender/appearance, trait-derived interests, cost-based planning alternatives, urgent interruption, cognitive priorities with retained deferred work, important-memory retention and repeated-event consolidation, directional relationship persistence, elapsed route interpolation, dialogue filtering, gifts and repetition penalties, card acceptance and persistent deck state, ownership, serialization, backup recovery and refusal of unsafe resets, and interaction session/distance/revision validation.

Tests supplement the runtime interactions and framebuffer captures; they do not replace them. `verifyWorldComesAlive` builds the remapped JAR and QA driver. The production task boots the remapped artifact, exercising the declared mixin refmap and production namespaces.

## Limits of the proof and remaining implementation

- Generation currently authors a reusable modular village/town/hamlet/trading-post slice with palette variations. The additional data archetypes reuse these building forms. The complete requested set of visually distinct capitals, castles, monasteries, ruins, dungeons and other locations is not authored.
- Regional connections and cached local road routes are real logical records. Physical roads between distant settlements, caravans, migration, ambush routing and a regional shipping economy remain to be implemented.
- Humanoid models have basic articulated movement, gender silhouettes, hair, tunics and boots. Broader appearance customization, profession-specific outfits and cinematic/social animations remain limited.
- Utility/GOAP decisions, schedules, emergency wakeups, local travel, memory, gossip, relationships and recurring activities execute. Perception, investigation, combat, guard responses and dynamic events are still narrower than the complete requested simulation.
- Farming/bakery/forge production, stock, purchases, wages and route-related shortage effects are functional. Full professions, recipes, quality systems, multi-town supply chains and player specializations need expansion.
- Pokémon exist as persistent civilian partners, visible companion entities and training activities. General NPC trainer battles, mounts, transport and species-specific complex work actions are not implemented.
- Card challenges and NPC-versus-NPC duels use the existing engine; collections/decks and skill persist. Deck tiers initially share the seeded deck. Autonomous card buying/trading, sophisticated deck rebuilding, large tournament progression and rich card-night spectators are incomplete.
- Social rules include contextual gifting, asymmetric relationships, dating/engagement gates and marriage/property records. Fully developed shared-household relocation, physical guest rooms, player-run businesses, festivals and bespoke relationship story events remain incomplete.
- Crimes depend on nearby line-of-sight witnesses and affect faction bounty, memory and reactions. Comprehensive trespassing/container theft hooks, reporting travel, trials, pursuit and Pokémon theft are incomplete.
- Content is validated/reloadable, but not every requested dialogue criterion is configurable. There is no implemented full interaction wheel, animation graph or continuously updated ambient relationship HUD.
- Runtime validation uses an integrated server plus a separate headless dedicated server. A two-client simultaneous multiplayer session and long-duration thousands-of-NPC profiling were not performed.
- The measured performance window covers settled simulation. Generating entirely new terrain produced visible chunk-generation stalls in this environment; the evidence does not establish stable 20 TPS during every new-terrain generation peak. There is no claim of uninterrupted 20 TPS at arbitrary population scale.

The source, reproducible QA driver, logs and limitations make the result reviewable. Completion of the entire original production specification remains a larger content and gameplay effort beyond this verified slice.
