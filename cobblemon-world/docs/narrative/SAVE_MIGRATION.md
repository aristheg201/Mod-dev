# Save migration and authored map points

`PlayerProgression.narrative` adds a null-safe schema, current main stage ID, finished stage IDs, chain cursors, completed chains, scene cursors, transcript, tendencies, loss counts, payment ledger and seasonal claims. Existing fields remain. Legacy contacts/messages retain their IDs; new phone follow-ups are appended.

The highest recognized authored victory milestone infers the preceding stages. Their flags and rematch eligibility are retained. Winning Vargan infers progress up to the School, not the new archive inspections. Existing fully completed saves retain completion and can enter postgame content. Missing fields normalize without replacing existing identities.

NPC positions remain in `npc_placements.json`. Inspection/travel points live separately in `narrative_points.json`; the map author places them with `/cworldpoi place <id>` while standing above the intended block. Default inspections use a lectern. The admin manifest is `world.json.pois`; ordinary players see translated place names and explicit objectives. No stock map coordinates are baked into quest prose.

The scammer uses a world-authored anchor and owner-bound runtime instances. Progression hides the actor after the first scene and permits its return for confrontation. Shared NPC recovery does not reconstruct it as a persistent mannequin. Saved crash residue fails the ordinary placement UUID check and is removed.

Seasonal entitlements preserve a reserved UUID before storage insertion, then a delivered state. Releasing or trading an obtained reward cannot reset its claim. BeastCoin rewards retain pending entitlements when BEconomy is absent; prepared but uncertain credits are held for reconciliation and logged, rather than automatically credited twice.

Test coverage and runtime limitations are recorded in the runtime QA report. Do not assume migration from an unknown production world was exercised merely because synthetic save tests pass.


## Saved native NPC repair in 0.3.1

The UUID and dimension in `cobblemonworld/npc_placements.json` are authoritative. Existing matching Cobblemon NPC entities receive their authored interaction and skin on load, even if their saved interaction is absent, native default dialogue, or an obsolete configuration. The same UUID, location, authored yaw and all player progression remain. Marking the NPC persistent prevents native distance despawn. Already missing entities still use recovery; no NPC is rebound merely because its name resembles an authored character.

Install the same remapped JAR on client and server and restart both. Keep world data and NPC placements. Do not reset progression or re-place the map NPCs as part of this update. The player's actual production world has not been supplied; its exact migration remains UNPROVEN. The report exercises an isolated save containing real persisted NPCs with the same class-default dialogue failure.
