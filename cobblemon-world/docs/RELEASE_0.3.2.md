# CobblemonWorld 0.3.2 — release candidate and evidence

Target: Minecraft 1.21.1, Fabric Loader 0.18.4, Java 21, Cobblemon 1.8.1. This is a remapped release build, not a development JAR. **Production acceptance is still open.** Internal gameplay evidence is separate from live-server evidence. No modified JAR, resource pack or progression file has been deployed to production during this release work.

## Defects repaired

| Defect | Responsible implementation | Change and evidence |
|---|---|---|
| Buying from Ren blocked the main story | `world.json` authored `ren_supplies` as a main `buy` stage; `NarrativeEngine.complete` advances only the current stage | Move the same stage into the zero-reward optional `ren_optional_supplies` chain. Opening gameplay reaches Mara without buying anything. |
| Existing players remain on the removed main stage | `NarrativeEngine.migrate` previously stopped at schema 1 | `NarrativeMigration` schema 2 maps only the old main cursor to `lan_errand`, preserves history/claims/flags/quests, and never grants rewards. Unit tests cover stuck, completed and repeated migration. Live existing-player migration has not been performed. |
| Refusal could complete a stage; merely viewing a side offer activated it | `ConversationService.choose` resolved terminal choices as completion regardless of refusal; offers activated before acceptance | Explicit `close` refuses; viewing an offer does not activate it; actual acceptance activates it. Branch tests cover all authored refusal paths. |
| First challenge/rematch selection and silent battle failure | `ConversationService.openNpc/choose`, `TrainerBattleService.startBattle` | First challenge comes from its actual story stage; rematch requires a recorded win. Check live physical binding, distance, ownership, story gate, current native battles, level cap and healthy party. Return the concrete localized reason and keep the conversation open on failure. Native Mara loss, retry and victory were observed. |
| Client closed or reset dialogue before server acknowledgment | `DialogueScreen`, networking, `ConversationService` | Server close payload; revision/session validation with resynchronization; selected player line and NPC reply remain visible; histories and personality are persisted. |
| Replayed shop requests lacked a transaction identity | `ShopBuyPayload`, `ShopSnapshot`, `ShopService` | Server-held UUID session, revision and bounded request-ID set; validate live NPC binding and proximity; reserve revision before debit/delivery; stale requests resynchronize. Both clients and server need 0.3.2. |
| An inactive POI hid another active POI at the same block | `PoiStore` interaction loop | Continue past inactive colocated entries; succeed only when the current interaction opens. The actual `wolf_track` failure was saved, patched, and replayed successfully. |
| Narrative follow-up phone messages disappeared after graph rewrite | `ContentRegistry` assumed a `facts` node | Use the authored answer/acceptance or battle-outcome node. Restore follow-up content without inventing a separate quest system. |
| Final encounter bypassed shared battle checks | `TobaEncounterService.beginPhaseOne` called the battle builder directly | Route the canonical proxy through the same `TrainerBattleService` checks; clean up rejected phase state; show an actual reason. Final battle verification is recorded separately below. |
| Corrupt progression could be replaced with an empty store | `ProgressionStore` loading/normalization | Fail closed on malformed/null player data and refuse destructive overwrite. Migration is no longer repeated on ordinary state reads. |

The previous teleport-to-void incident belongs to ChunkyBorder; this release does not globally replace Minecraft teleportation. The source was compared with the installed 0.3.1 internal JAR (SHA-256 `22a4626ce6e13ed104a2078f7e25e05a2fda02717b0d01a3d12056dd47b014e2`). Temporal correlation was not used as evidence against CobblemonWorld.

## Narrative handoff

There are 69 main stages, 45 optional chains, 369 scenes and 30 trainer teams. Vietnamese scene graphs, distinct actor greetings, battle outcomes, relevant acceptance/question/refusal responses and 17 phone conversations were rewritten. The 51 legacy phone replies have separate acknowledgments and preserve their IDs and outcomes. Main-story English text and phone text were rewritten; secondary English side-content retains valid legacy localization in places and needs an additional editorial pass.

Read `narrative/VOICE_REFERENCE.md`, `narrative/TRANSCRIPTS_VI.md` and `narrative/PHONE_TRANSCRIPTS_VI.md` for actual text, not just localization keys. `MAIN_STAGES.md`, `SIDE_CHAINS.md` and `CAMPAIGN_FLOW.md` map requirements, optional activities, events and NPC targets. Ren, fashion, bicycle browsing and purchases remain optional. The opening item delivery remains a legitimate story errand; QA fixtures supplied those items and do not prove normal-world resource acquisition.

Authoring sources are under `tools/narrative`. Run `rewrite.py`, then `rewrite_phone.py`, followed by `tools/validate_narrative.py` and `tools/document_narrative.py`. Do not run the older baseline authoring script over these rewritten resources. Validation checks references, graph reachability, actors, teams, actions, localized text and the absence of main-story purchase stages. Automated coherence checks do not substitute for human editorial review of every line.

## Runtime evidence and limits

Real Linux graphical Minecraft clients ran using Xvfb/Mesa. Screenshots are framebuffer captures. No Windows Bestiary installer was executed. Dedicated offline non-OP QA accounts connected to isolated localhost Fabric servers using the actual remapped release and native Cobblemon battle registry. Fixtures supplied placements, POIs, currencies, delivery items and legal test parties; they did not forge victory events. The campaign fixture used strong legal Pokémon and does not assess normal-player battle balance.

The production panel, logs, exact mod versions/configuration and Spark profiles were accessed read-only. Production raw Minecraft TCP is not permitted by the environment. The user requested internal testing instead of waiting for a human QA account. Internal tests do not establish compatibility with the entire 213-mod production pack, SVFrameMMO progression or all production map placements. Only 11 NPC anchors were present in the production snapshot; internal placement of the complete cast does not place them on production.

See the accompanying QA table and runtime log bundle for PASS, FAIL and NOT TESTED. Historical 0.3.1 reports in this source tree are historical evidence, not new 0.3.2 passes. Critical unverified production cases prevent a claim of full completion.

## Installation and rollback

See the bundle's `DEPLOYMENT_AND_ROLLBACK.md`. Read `narrative/SAVE_MIGRATION.md` before installing. Replace the old CobblemonWorld JAR on both server and clients; never keep both versions. Do not enable any `cworld.qa.*` fixture JVM property on production. Back up the entire save plus progression, factions, NPC/POI stores and economy stores before the first upgraded start. Do not delete progression files. Downgrading after schema-2 writes requires restoration of the matching pre-upgrade data, not merely the old JAR.
