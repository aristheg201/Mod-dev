# Save migration and authored map points

## 0.3.1 → 0.3.2

The narrative schema is now 2. Migration runs only when the stored schema is older; restarting repeatedly does not replay it. `ren_supplies` is removed from the main campaign and retained under the optional, zero-reward chain `ren_optional_supplies`.

A schema-1 player whose current main stage is `ren_supplies` moves to `lan_errand`, the next legitimate story errand. This does **not** mark a purchase completed or grant rewards, items, currency or a battle win. A previously completed `ren_supplies` remains in the finished-stage history and marks its optional chain completed. Contacts, flags, other finished stages, side-chain cursors, claims, tendencies and dialogue history are preserved. A schema-0 save still infers its existing recognized victory milestones before upgrading.

Old authored scene cursors are retained. If a cursor refers to a node removed by the dialogue rewrite, opening that scene selects a valid start node without deleting the saved transcript. This does not independently complete the quest or replay a reward.

Back up `<world>/cobblemonworld/` before deployment. Do not reset progression files. The store now refuses to overwrite a progression file whose JSON or player entries could not be loaded; repair or restore that file instead of allowing an empty autosave to erase it.

CobblemonWorld 0.3.2 changes the shop request protocol to include a session UUID, revision and unique request ID. Update the mod on both server and clients together. An old 0.3.1 client is not a compatible QA client for the new purchase protocol.

`PlayerProgression.narrative` adds a null-safe schema, current main stage ID, finished stage IDs, chain cursors, completed chains, scene cursors, transcript, tendencies, loss counts, payment ledger and seasonal claims. Existing fields remain. Legacy contacts/messages retain their IDs; new phone follow-ups are appended.

The highest recognized authored victory milestone infers the preceding stages. Their flags and rematch eligibility are retained. Winning Vargan infers progress up to the School, not the new archive inspections. Existing fully completed saves retain completion and can enter postgame content. Missing fields normalize without replacing existing identities.

NPC positions remain in `npc_placements.json`. Inspection/travel points live separately in `narrative_points.json`; the map author places them with `/cworldpoi place <id>` while standing above the intended block. Default inspections use a lectern. The admin manifest is `world.json.pois`; ordinary players see translated place names and explicit objectives. No stock map coordinates are baked into quest prose.

The scammer uses a world-authored anchor and owner-bound runtime instances. Progression hides the actor after the first scene and permits its return for confrontation. Shared NPC recovery does not reconstruct it as a persistent mannequin. Saved crash residue fails the ordinary placement UUID check and is removed.

Seasonal entitlements preserve a reserved UUID before storage insertion, then a delivered state. Releasing or trading an obtained reward cannot reset its claim. BeastCoin rewards retain pending entitlements when BEconomy is absent; prepared but uncertain credits are held for reconciliation and logged, rather than automatically credited twice.

Test coverage and runtime limitations are recorded in the runtime QA report. Do not assume migration from an unknown production world was exercised merely because synthetic save tests pass.
