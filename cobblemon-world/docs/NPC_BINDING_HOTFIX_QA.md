# Saved-NPC dialogue and persistence fix — 0.3.1

## Artifact and installation

Production-remapped artifact: `CobblemonWorld-0.3.1-internal.20261008.jar`.

SHA-256: `22a4626ce6e13ed104a2078f7e25e05a2fda02717b0d01a3d12056dd47b014e2`.

Repository: `aristheg201/Mod-dev`; branch: `feature/cobblemon-world-rpg-20261006`; module: `cobblemon-world`. The delivered release manifest records the final commit and full-source ZIP checksum.

Replace the old CobblemonWorld JAR on both client and server with this JAR and restart both. Keep the world, `cobblemonworld/npc_placements.json`, progression and faction files. Re-placing NPCs and resetting the story are not part of this update. No additional mod is required by the repair.

## What failed

The player's screenshot shows an authored Professor Hale nameplate with Cobblemon's native greeting addressed as the player's name: “Hello, I'm Arisgrindel!”. A real client reproduced the same path with “Hello, I'm CWorldQA!” after the saved NPC's interaction was absent. Ren was also saved with an explicit native dialogue configuration. These inputs represent the failing interaction state; the player's actual world files were not available for comparison.

The previous recovery accepted any NPC entity with the placement UUID. It never restored a missing/default interaction. The entity-load handler also considered only NPCs already using `CWorldNpcInteraction`, so it skipped precisely the broken saved entities. The previous restart QA checked pose and phone persistence, without re-clicking a saved NPC. It did not cover this failure.

The expanded test then caught a second issue: the native `standard` NPC class can distance-despawn. Disabled AI and a nameplate do not make it persistent. Traveling from the service area back to Hale caused Elle and Tomo to disappear beyond the native distance threshold; recovery then assigned replacement UUIDs. Those two interrupted diagnostic runs are preserved as failures, not completed QA. A chunk-ticking check and missing grace period alone did not fix distance despawn.

## Source changes

`NpcBindingService` resolves authored identity from the placement's entity UUID and dimension, restores the actual existing entity's canonical interaction/name/skin, and marks it persistent. The load callback runs this before the first native despawn tick. Startup also checks already loaded entities after saved stores are available. A server entity-use callback repairs before Cobblemon selects its interaction, retaining native editor/battle guards and executing one normal interaction.

The same entity and party remain; the repair does not initialize a new NPC, award progression, reset a quest, rebuild a party, or write player/faction state. Custom NPCs made after the update are persistent from creation. Skin restoration uses Cobblemon's native texture API and bundled authored resources.

The placement store indexes UUID/dimension identities. Ambiguous duplicate UUIDs fail binding and log an explanation. An unrelated native NPC named “Professor Elias Hale” is deliberately left alone. Recovery of a genuinely absent NPC requires a nearby player, a ticking chunk with loaded entities, and a sustained missing interval. Normal chunk unloading is not evidence of deletion.

## Actual runtime method

An actual Minecraft client connected as the non-OP survival player `CWorldQA` to a separate Fabric dedicated server on localhost. Both loaded the production-remapped JAR. The isolated fixture copied a prior QA world, placed five real NPC entities, saved absent/native dialogue configurations and missing skins, and exited both processes. The pre-fix reproduction used a 0.3 artifact with opt-in QA controls added; its screenshots are labeled **before**, not 0.3.1 success evidence.

The successful upgrade restarted that saved world on the exact 0.3.1 SHA-256 above. The client used ordinary entity-interaction packets and real dialogue/merchant screen handlers. Client assertions required the dedicated CobblemonWorld screen, correct speaker/catalog, and actual choices. Server assertions compared each original saved UUID and anchor, upright pitch, disabled AI, persistent status and actual native saved texture bytes against the authored PNG. The player chose a real Hale conversation to advance `hale_phone`, and a damaged party Pokémon actually healed through Mira's service.

Both processes exited normally, then started again on the same JAR and saved world. The second run re-clicked all five NPCs, rather than checking their class/pose alone. It also repeated actual Mira healing. Original placement data remained byte-equivalent as parsed records, including UUID, dimension, coordinates, authored yaw and pitch. Saved story, personality and completed-quest sentinels remained. A native NPC with the same visible name but no placement identity was not rebound or discarded.

Screenshots are unmodified actual Minecraft framebuffer captures at 1280×720. `upgrade/` and `restart/` contain separate real captures and raw logs. Before/failing diagnostic evidence is separated from passing release evidence. Times in raw logs are UTC.

## Feature status on 0.3.1

| Feature | Status | Actual evidence |
|---|---|---|
| Default Hale interaction restored in saved world | PASS | Real before-native greeting; upgrade and second restart open the authored custom screen with Professor Hale, Vietnamese copy and choices. |
| Response-specific conversation and gameplay effect | PASS | Hale scene traversed through actual screen choices; named player reply appears and `hale_phone` completes. |
| Ren after saved native dialogue configuration | PASS | Dedicated PokéMall screen with canonical nonempty catalog and BeastCoin balance on both runs. |
| Elle after saved absent interaction | PASS | Dedicated fashion screen with actual item catalog on both runs; not native chat/dialogue. |
| Tomo after saved absent interaction | PASS | Dedicated bicycle screen/catalog on both runs, with registered MapKit dependency. |
| Mira actual heal after repair/restart | PASS | A real party Pokémon starts with HP 1; normal service action restores full health on both runs. |
| Same entity identity and orientation | PASS | All five original UUIDs and placement records match after upgrade and after the second process restart. Actual NPCs remain anchored with pitch zero. |
| Authored skins recover | PASS | Native serialized texture bytes/model match each authored PNG; actual Hale and Mira world screenshots show restored models/skins. |
| Distance despawn prevention | PASS | Same travel sequence that previously replaced Elle/Tomo now retains both original UUIDs, including travel back to Hale and the second restart. |
| Unowned same-name NPC | PASS | Actual separate native NPC is still present with its native interaction after both runs; no name-based takeover. |
| Saved player data | PASS, representative | Story sentinel, completed-quest sentinel and personality survive both runs; Hale adds its actual chosen conversation/progression. This is not exhaustive validation of a production world's data. |
| Fresh opening and native Mara battles | PASS | Fresh save completed all 11 Act-0 stages plus actual Mara loss. Native loss at ace 5/5 left the battle stage incomplete; actual rematch at ace 100/100 won, followed by investigation, first unknown message and aftermath. |
| Local Java/Loom build and tests | PASS | Java 21 local build/remap and 12 existing JUnit tests passed; narrative/data validators passed separately. They are not runtime feature proof. |
| All other campaign/side/Legendary/shop-security/faction/war paths | UNPROVEN on 0.3.1 | Not re-run in full for this focused repair. Their previous, explicitly scoped results remain in `PRODUCTION_RUNTIME_QA.md`; no old result is relabeled as a new complete run. |
| Player's exact production world and installed mod pack | UNPROVEN | Only the screenshot was supplied; tests use a separate persisted world containing the reproduced failing state. UUID-backed placements are required. Unregistered native NPCs are not automatically guessed from their visible names. |

The successful repair profile completed **16 steps**, the real restart profile completed **15 steps**, and the fresh opening completed **12 steps** on the delivered artifact. Final upgrade ended at 15:30:55 UTC, restart at 15:33:09 UTC, and fresh opening at 15:37:02 UTC on 2026-10-08. Earlier interrupted attempts are not included in those passing counts. UI screenshots plus canonical state verification establish these paths; grep/class existence/compilation alone are not the evidence.

## Exact runtime

Minecraft **1.21.1**; Fabric Loader **0.18.4**; Fabric API **0.116.17+1.21.1**; Cobblemon **1.8.1+1.21.1**; Temurin Java **21.0.12.1+1**; Gradle **8.12**; Loom **1.10.5**.

Optional dependencies present: supplied **BEconomy 1.5**, Cobblemon Armory **1.5.4 Fabric 1.21.1**, official Cobblemon Armors **1.6.0+1.8.1**, MapKit **1.0.9-SNAPSHOT** from the supplied 1.0.9 release, Fabric Language Kotlin **1.13.6+kotlin.2.2.20**, GeckoLib **4.9.2**, Placeholder API **2.4.2+1.21**. The Armors release's erroneous native dependency label still uses the same test-only Fabric metadata override documented in the baseline report.

## Re-running focused checks

The prepared isolated world lives under `qa-runtime/server-npc-binding`. `tools/run_production_qa.sh binding-upgrade` runs actual repair/player paths; `binding-restart` skips replaying Hale's completed opening scene and re-clicks the saved actors after a real process exit. Supply Java 21, runtime dependencies, a display and the usual dependency/proxy settings as documented in `PRODUCTION_BUILD.md`. These fixtures must never be enabled on a player server.

The `cworld.qa.npcBindingFixture` before-native-greeting control was run with the historical pre-fix QA artifact. It is a diagnostic reproduction, not an expectation that the fixed artifact should display native default dialogue. Runtime directories, disposable caches and downloaded optional mods are excluded from the full buildable source ZIP.
