# Production and narrative runtime QA — 2026-10-08

## Artifact, provenance and method

The delivered production-remapped artifact is `CobblemonWorld-0.3.0-internal.20261008.jar`, SHA-256 `6a69eeb0b1d3bf8bed98a342e205e7abe62214360ea95d70675bc568b929b0bc`. The release manifest records the delivered SHA-256 and exact Git commit. All work is in `aristheg201/Mod-dev`, branch `feature/cobblemon-world-rpg-20261006`, module `cobblemon-world`.

Testing uses an actual Minecraft client and a separate Fabric dedicated server, both loading the remapped artifact. `CWorldQA` is a survival player with no operator permission. The isolated, offline server binds localhost. Opt-in fixtures author blank platforms/NPC placements, supply items and legal Pokémon, and set balances; they are disabled in normal play. Actual entity/block interactions, GUI mouse handlers, purchase packets, native capture and Cobblemon move/switch choices exercise gameplay. Trainer wins are native battle outcomes, not injected victory flags. Peaceful mode prevents ambient mobs from interrupting transport checks.

The expanded campaign was exercised over resumed diagnostic sessions. `narrative-attempt9` covers opening through Marlow's photograph (main stages 1–38), including an actual Mara loss and rematch. Its earlier 0.3 diagnostic revision is not represented as a complete run on the delivered hash. The `61e30492f6e740533da70a9f0d96ab02e408acd4fbe8c983917b1e93b8906d72` candidate covers stages 39–70, eleven complete regular side chains and Weather Duo stages 1–20, then an actual loss at its Kyogre guardian. That loss left the chain at stage 21, with no Legendary claim. The final JAR resumes that same saved world using legal Focus Sashes on the QA player's party, leaving the opposing roster/AI unchanged.

The final artifact differs from the 61e304 candidate in exactly six JAR entries: English/Vietnamese locale files and four opt-in QA classes. Gameplay classes and canonical world data are byte-identical. The locale change gives 29 trainers distinct win/loss prose and removes obsolete seal references. The QA changes add early gate assertions, stronger stored-reward checks, native PC screenshots and the rain-counter held item. The raw entry comparison is included with the evidence. The driver’s `main=178` log field counts all finished IDs, including side stages and battle markers; it is not a count of main-story stages. Final opening, services, real process restart and missing-economy profiles are separately identified; an interrupted diagnostic run is not labeled a successful completed profile.

Screenshots come from Minecraft's framebuffer at 1280×720 using its screenshot API, with occasional direct X11 framebuffer captures. No screenshots were manufactured. An index states each image's path and provenance. Local tests and content validators supplement, rather than replace, runtime evidence.

## Exact tested runtime

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.116.17+1.21.1 |
| Cobblemon | 1.8.1+1.21.1 |
| Java | Eclipse Temurin 21.0.12.1+1 |
| Gradle / Loom | 8.12 / 1.10.5 |
| Fabric Language Kotlin | 1.13.6+kotlin.2.2.20 |
| BEconomy | Supplied 1.5 API; upstream metadata contains the literal `${version}` |
| Cobblemon Armory | 1.5.4 Fabric 1.21.1 |
| Cobblemon Armors | Official 1.6.0+1.8.1 release from the supplied Modrinth link |
| Cobblemon Map Kit | 1.0.9-SNAPSHOT from the supplied 1.0.9 release link |
| GeckoLib | 4.9.2 Fabric 1.21.1 |
| Placeholder API | 2.4.2+1.21 |

`RUNTIME_DEPENDENCIES.json` contains filenames, URLs and hashes. The official Armors release mistypes its Cobblemon dependency as `1.8.1+1.12.1`; QA uses Fabric's metadata override for `1.8.1+1.21.1`, without patching item classes. The separately named server compatibility JAR was not supplied for binary comparison. SVFrame integrations were absent.

## Runtime results

PASS applies only to the stated, actually exercised path. Final-profile logs identify exact coverage. The services profile completed 92 steps on the final JAR. Services restart completed four steps; missing-economy completed three. All final client/server tasks exited successfully. Weather completion and native-PC verification ended at 11:48 UTC, independent reward restart at 11:54, fresh opening at 11:52, services at 11:58, services restart at 12:00 and missing economy at 12:01.

| Feature | Status | Evidence and practical scope |
|---|---|---|
| 70-stage campaign | PASS, combined revisions | Actual ordered stage paths across the recorded sessions: opening, towns 2–8, Rocket investigation, False Victory, Tower/League, Wolf, separate archive records, cast reactions, final battle, explanation and epilogue. Not one uninterrupted final-hash run. |
| Dialogue box / branching | PASS | Real dedicated dialogue screen, four personality choices, response-specific turns/effects, named player reply, continued scene, Vietnamese wrap and Orin's long-text wheel scroll. Conversations remain out of ordinary chat. |
| Conversation journal | PASS | Archive conversation closed/reopened with the same player reply and transcript; the real History control displayed persisted conversation lines. |
| Soft personality memory | PASS, answer effects; callbacks limited | Persisted answer tendencies include sarcastic 45, serious 33, polite 38, greedy 36, chaotic 2 and sleepy 2 in the completed run. The final scene uses the sarcastic callback; not every tendency/scene combination or historical callback wording was checked. |
| Mara loss / rematch / scaling | PASS | Actual loss with player/trainer ace 5/5 preserved the main step; native rematch ace 100/100 won. An upgraded six-member postgame rematch completed. |
| Competitive authored teams | PASS, configuration and transport | Runtime factory resolved all 145 members across 30 teams. Native battle snapshots audit actual IVs, EVs, nature, ability, moves, held items and skill 5. All major trainers launched real battles. This is not a human difficulty rating. |
| Town 8 / False Victory | PASS | Liora's local arc, harbour manifest inspection, Dorian's actual rain-team qualifier and recognition/Mara follow-up preceded Tower registration. |
| Battle Tower | PASS | Registration then actual Rowan → Nyx → Orion victories, with distinct conversations/teams. |
| Royal League | PASS | Steward then Cassian → Seraph → Kael → Aurelia victories and Aurelia aftermath, before Wolf access. |
| School of Wolf | PASS | Bran → Fen, actual trail inspection, Skoll, consumed delivery supplies, Hati, Vargan. Vargan unlocked access without silently completing archive inspections. |
| Archive / TOBA / epilogue | PASS | Three separate lecterns, cast reaction scene, final unknown contact, story-spawned encounter, actual native trainer victory, identity/explanation, consequence and Hale/Mara epilogue. No active resonance-heart progression. |
| Regular side content | PASS, 11 complete chains | VAR, Con nhà người ta, Thông não, scam, delivery, Magikarp rescue, Ren's labels, Mara postgame, harbour parody, old man's warnings and sleep. Actual mixed mechanics and persistence; 32 additional regular chains remain unplayed end-to-end. |
| Native capture / rescue / heal | PASS | Thrown native Poké Ball caught wild Magikarp; the canonical species capture hook advanced the chain, then Mira restored its actual health. |
| Recoverable scam | PASS | Risky dialogue debited 10 BC, gave a real dead bush and hid the owner-bound trader; investigation/confrontation completed with the refundable loss and ordinary chain reward. |
| Weather Duo investigation / sightings | PASS, stages 1–20 | Actual coastal/inland interactions, relic deliveries, native uncatchable/unbattleable Groudon and Kyogre sightings, guardian battle and balance restoration. First sighting camera framing is close; PC/model captures provide clearer reward views. |
| Weather Duo guardian loss safety | PASS | Kyogre guardian beat the fixture party; loss count became 1, cursor stayed at stage 21, and claims remained empty. Opposing stats/AI were not weakened for retry. |
| Both Legendary rewards / quality / duplicate guard | PASS | Final resume completed Weather Duo 21–24. Groudon and Kyogre each routed to native PC with a full party; level 70, intended nature/ability, six IV 25, four moves and repeated claim attempts verified. |
| Legendary real restart / native PC | PASS | Both processes restarted again, read the same saved UUIDs exactly once, verified reward quality and selected each actual Pokémon through native PC mouse input. Zero remaining quest stages were replayed. |
| Fresh final opening / first unknown message | PASS | Fresh final-JAR save completed all 11 Act-0 stages plus the intentional Mara loss. First contact was absent before the investigation and present afterward; native rematch/win and new outcome prose exercised. |
| Early Tower/League/Wolf/final gates | PASS | Fresh final services world interacted with Tower, League, Bran and Vargan before prerequisites; blocked dialogue exposed no actionable stage/rematch. Final eligibility stayed false before archive. |
| First-party Phone / branching Messages | PASS | Final services opened Trainer Card, Objective, Current Story, Side Quests, Level Cap, Badges, Contacts, Messages, League and Faction. Reply 2 saved named CWorldQA, branch-specific response/effect and transcript. |
| Dedicated Ren / Elle / Tomo shops | PASS | Ordinary final-JAR interactions opened independent ShopScreen. Ren bundle purchase and Tomo exact-500-BC bicycle grant worked; Elle showed actual ItemStacks, categories, detail, scroll and maintained state. |
| Full Armory + Armors reachability | PASS | Independent final runtime registry/catalog equality audit matched 262 eligible items: 209 Armory plus 53 Armors. Internal creative marker excluded; counts observed, not hardcoded. Every entry had a category and canonical 1–500 BC price. |
| BeastCoin transactions | PASS | Final 0/19/20/high balance cases, HunterCoin-primary isolation (9000 unchanged), full inventory rejection, injected post-grant inventory rollback/exact refund, rapid two-request rejection, unknown entry and negative quantity all exercised with actual C2S requests. |
| Resource pack / GUI scales / languages | PASS | Final real resource pack changed the selected Poké Ball model to diamond and disabling restored it. Elle ran at GUI scales 2/3; English and Vietnamese reloads showed localized Phone, Messages, Faction and Ren controls. |
| NPC anchor / facing / yaw / restart | PASS | Downward placement normalized pitch 0; forced displacement returned to anchor. Interaction faced the player, authored yaw 72 survived store reload and a real client/server process restart. Recovery verified location, pitch 0 and yaw tolerance under 0.1°. |
| Stable pin / immediate navigation | PASS | New-chain pins and final legacy pin saved stable identity and resynced immediately. Actual HUD showed yaw-relative arrow/moving distance; re-placing Mara resolved the new store position. Missing placement and Nether target states handled; both reset aliases updated immediately. |
| Native faction basics | PASS | Final normal Phone requests created a native faction, rejected owner leave and disbanded it. Native state remains first-party; multiplayer permutations remain unproven. |
| Shop / message real restart | PASS | Final services restart completed four steps: Mira placement/orientation recovered, named reply-2 transcript survived, and 60 BC plus 16 purchased Poké Balls remained. |
| Missing BEconomy | PASS | Final artifact launched client/server without BEconomy. Ordinary Ren interaction opened the real disabled shop; snapshot reported economy unavailable and a purchase request granted no item. All three steps and clean process exit passed. |

## Build and content validation

Twelve JUnit tests cover narrative structure/identity, transactions and level scaling. The Python content validator checks 70 main stages, 43 regular substantial chains, the separate 24-stage season, 369 scenes, 30 teams, unique stable IDs, graph destinations, localization parity, progression gates, reward references and shop price limits. Runtime registry construction checks actual species/form moves, abilities, items and team configuration. Static validation alone is not marked runtime PASS.

The included source was separately copied/unpacked outside the working checkout and built locally with Java 21, Gradle/Loom and the documented dependencies. The independent final-source build generated the exact same `6a69eeb0b1d3bf8bed98a342e205e7abe62214360ea95d70675bc568b929b0bc` remapped JAR. The release records the final source archive checksum separately. No GitHub Actions compile-fix loop was used.

## UNPROVEN and limits

- **UNPROVEN — human battle difficulty and normal reward pacing.** Legal fixture parties exercise native strategy/progression; they do not establish fair ordinary campaign balance or farming time.
- **UNPROVEN — final-revision main stages 12–38 as one repeat run.** Those stages were actually played in the earlier 0.3 diagnostic session; final opening and the 61e304 resumed run cover the remaining campaign. The report does not claim one complete campaign on the final hash.
- **UNPROVEN — all 43 regular chains end-to-end.** Eleven full chains plus Weather Duo are runtime targets; the other 32 have authored content/validated hooks, not full player-path proof.
- **UNPROVEN — every trainer loss/rematch, response permutation and tendency callback.** Mara and the Kyogre guardian losses are exercised; other loss voices are authored, not individually played.
- **UNPROVEN — multiplayer faction invites/accept/roles, full island-war event and owner-instance appearance isolation.** Existing native implementations remain; a single client cannot prove multi-faction combat or concurrent actor visibility.
- **UNPROVEN — installed SVFrameLib/SVFrameMMO compatibility.** Absence fallback runs; matching installed binaries were unavailable.
- **UNPROVEN — migration of the user's production world.** No production save was supplied. Null-safe legacy inference and local migration tests do not prove that particular world.
- **UNPROVEN — abrupt process crash at the cross-store Legendary insertion boundary.** Progression JSON and native Cobblemon storage have no shared atomic transaction. Reserved UUID reconciliation prevents duplicate insertion while delivered claims never recreate traded/released Pokémon. Clean restart is tested separately; sudden-crash recovery needs storage-boundary fault injection/operator reconciliation.
- **UNPROVEN — completely exhausted PC, rare storage/backend failure, repeated currency refund failure, every owner reload/override and every level-cap capture/spawn/experience permutation.** Do not infer those from one full-inventory test.
- **UNPROVEN — audible feedback.** The software-rendered Xvfb client has no usable OpenAL device. Screen state/success feedback is visible; sound was not heard.

The map owner must author NPC/POI locations and the final meeting anchor. No production world or medieval reference image was supplied. Visual direction uses original wood/parchment/red-and-gold assets. Screenshots are actual QA platforms rather than an invented finished map. The existing separate second boss phase was already inactive in the base; this pass proves the final native trainer battle and aftermath, not a new two-phase boss.

Groudon/Kyogre are native species with valid battle/storage data, but Cobblemon 1.8.1 contains no model/texture/poser assets for either. The actual PC therefore displays its upstream Substitute fallback, as the supplied screenshots show. A compatible Pokémon resource pack can supply their models; this pass does not claim they have native dedicated visuals.

Upstream warnings include offline Mojang public-key lookup failure, missing audio device, an Armory glove texture and a Cobblemon recoil animation. These are recorded, not hidden. None is by itself evidence of a CobblemonWorld purchase/dialogue/progression success.
