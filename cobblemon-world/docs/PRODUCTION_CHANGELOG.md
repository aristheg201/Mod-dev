# Saved-NPC production fix: 0.3.1

Saved Professor Hale and service NPCs now recover their CobblemonWorld interaction, name, skin, authored facing and anchor in place. Native default interactions are repaired using the placement UUID and dimension, without replacing the entity or resetting story, parties, quests or faction data. An unrelated native NPC with the same visible name is not claimed.

Map NPCs are marked persistent at creation and during entity loading, before vanilla can distance-despawn them. Recovery also waits for a nearby, ticking, fully loaded entity chunk and a sustained missing interval before replacing a genuinely absent NPC. This prevents a transient chunk transition from changing placement UUIDs.

The load path, startup path and server interaction callback restore native behavior through normal source/API integration. No bytecode patch, reflection helper, chat conversation or command shop is introduced. Build and tested coverage are recorded in `NPC_BINDING_HOTFIX_QA.md`; the previous narrative report below covers the 0.3 release.

# Production and narrative changelog: 0.3

The campaign now has seven Acts and 70 gameplay stages, with a substantial Rocket investigation, Town 8, False Victory, ordered Tower/League progression, an archive after Vargan, a final explanation and an epilogue. Recurring contacts and response-specific phone follow-ups preserve the first-party Trainer Phone.

NPC conversations have their own server-held sessions and custom dialogue screen, including choices, wrapped text, reply turns, scrolling and a persistent conversation journal. Vietnamese and English are authored together; every trainer has a distinct win/loss voice and major cast tendencies have callbacks.

Forty-three regular substantial side chains add investigation, family stories, deliveries, rescues, services, collection, actual capture/heal hooks, rematches and a recoverable ten-BeastCoin scam. Weather Duo adds a separate 24-stage postgame season, real Legendary sightings and independent one-time Groudon/Kyogre partnership rewards with full-party PC routing.

Thirty competitive rosters use six-31 IV defaults, explicit tactical exceptions, progressive EV investment, sensible natures, legal form-aware moves, supported abilities and held items. Levels scale to the current player’s party; native skill-5 battle AI remains responsible for tactics. Real losses preserve progression and allow rematches. Closed native battle IDs are reconciled so anchored NPCs cannot remain stuck after a recall animation.

Dedicated shops, runtime Armory/Armors catalog enumeration, canonical BeastCoin prices, secure transactions, stable pins/navigation, native faction/island-war state and anchored NPC presentation remain in normal source. Save migration retains old flags, contacts, transcripts and placements while inserting archive discoveries safely.

Detailed content lists, character arcs, teams, shop economy and migration notes are under `docs/narrative`. Actual runtime and visual coverage is in `PRODUCTION_RUNTIME_QA.md`; version 0.2’s earlier reports are archived under `docs/historical` and are not evidence that a newly added feature passed.
