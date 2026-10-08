# Production runtime QA — 2026-10-08

## Artifact and method

The delivered production-remapped artifact is `CobblemonWorld-0.2.0-internal.20261008.jar`, SHA-256 `16251dc86a98d7462d3b3171f218ee089d4039a87b05a9e7459dac0f9882a093`.

The 147-stage full campaign ran on candidate SHA-256 `e9d1b008e36977adee09b9a7c3b870023b6094a7997c9ffc07badb39fb6270b1`. Short restart/dependency profiles subsequently exposed a QA teardown error: the legacy client performance check ran before it had 20 real samples. The final build adds a bounded wait for those samples, without changing thresholds. Its only changed JAR entry is the opt-in `qa/CWorldQaClientHarness.class`; every gameplay, networking and resource entry is byte-identical to the full-run candidate. Restart and missing-economy profiles are rerun on the final JAR. Full-run screenshots/logs retain their original candidate identity; they are not presented as a second full campaign on the final hash.

Testing uses an actual Fabric Minecraft client and a separate dedicated server, both loading this remapped JAR. The player is `CWorldQA`, survival, with an empty operator list. The isolated server binds localhost, uses offline authentication, and runs peaceful mode to keep ambient mobs from interrupting transport tests. The server fixture prepares NPC locations, currency balances, inventory states and legal Pokémon parties. Shop purchases, dialogue replies, phone controls, NPC interaction and battle actions travel through the actual client/server paths. Battle progression is checked from real Cobblemon victories; it is not granted by injecting victory flags.

The opt-in test driver coordinates the client and server and captures Minecraft's actual framebuffer at 1280×720. It does not synthesize screenshots. QA properties are disabled in normal play. The full gameplay-candidate campaign ran from 03:20 to 03:45 UTC on 2026-10-08 in a fresh world and finished all 147 stages with no QA failure. The final-JAR restart and missing-economy runs finished at 06:33 and 06:35 UTC respectively, and both client/server tasks exited successfully. One additional live battle capture used the X11 framebuffer directly. Raw logs are supplied with the visual evidence.

Local unit/data validation supplements these runtime results. Eight transaction/scaling tests passed. A separate unpacked source checkout built with the included wrapper and generated a byte-identical remapped JAR. No GitHub Actions compile-fix loop was used.

## Tested versions

| Component | Exact tested version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.116.17+1.21.1 |
| Cobblemon | 1.8.1+1.21.1 |
| Java | Eclipse Temurin 21.0.12.1+1, Java 21 |
| Gradle / Loom | 8.12 / 1.10.5 |
| Fabric Language Kotlin | 1.13.6+kotlin.2.2.20 |
| BEconomy | Supplied 1.5, `org.krripe.beconomy` API; upstream metadata literally says `${version}` |
| Cobblemon Armory | 1.5.4 Fabric 1.21.1 |
| Cobblemon Armors | Official 1.6.0+1.8.1 Modrinth release |
| Cobblemon Map Kit | 1.0.9-SNAPSHOT, artifact from the user's 1.0.9 version link |
| GeckoLib | 4.9.2 Fabric 1.21.1 |
| Placeholder API | 2.4.2+1.21 |

Dependency filenames, download URLs and SHA-256 values are in `RUNTIME_DEPENDENCIES.json`. The official Armors download declares a mistyped Cobblemon dependency, `1.8.1+1.12.1`; the isolated QA uses Fabric's metadata override to declare `1.8.1+1.21.1`. Its item classes were not patched. The user's separately named `01-cobblemon-armors-1.6.0-1.8.1-compat.jar` was not available for binary comparison.

## Feature results

`PASS` means the stated path was exercised in the actual game. It does not imply every possible multiplayer or failure permutation was tested.

| Feature | Status | Actual evidence and scope |
|---|---|---|
| First-party Trainer Phone | PASS | Normal mouse navigation opened Trainer Card, Objective, Current Story, Side Quests, Level Cap, Badges, Contacts, Messages, League and Faction. Home and app screenshots are included. |
| Branching Messages | PASS | Multiple choices rendered; scrolling reached reply 2; choosing it saved the named `CWorldQA` reply, generated the branch-specific NPC response and quest state, and refreshed the phone. Reopening retained the transcript. The speaker remained `???` before reveal. |
| Vietnamese and English | PASS | Actual locale reloads, Vietnamese Messages/Faction/Ren screenshots, and English restore. Shop controls, status, objective text and dialogue use translation keys. Third-party item translations remain owned by those mods. |
| Mira healing | PASS | Fixture Pokémon began at 1 HP. Ordinary NPC interaction restored actual party health to its maximum. |
| NPC anchor and pitch | PASS | Placement with a downward player view produced pitch 0. Forced displacement returned to the stored anchor. Authored yaw 72 persisted through a placement-store reload. The actual NPC stands upright and faces the player after interaction. |
| Ren PokéMall | PASS | Ordinary interaction opened the dedicated shop screen with 15 resolved entries, real item models, canonical bundle prices and BeastCoin balance. No clickable-chat shop or phone snapshot was used. |
| Elle fashion inventory | PASS | Runtime registry audit exactly matched all 262 eligible registered items: 209 Armory and 53 Armors. Only Armory's internal creative marker was excluded. The implementation derives inventory from the registry; 262 is an observed result, not a hardcoded target. |
| Elle controls and visuals | PASS | All, Accessories, Armor, Weapons, Materials and Shiny filters exercised. Actual wheel scrolling reached row 9; selected-item details rendered for Zacian Sword and an Armors item. Screens captured at GUI scales 2 and 3. |
| ItemStack/resource-pack rendering | PASS | Actual Armory/Armors models rendered. Enabling a real test resource pack changed the selected Poké Ball's model to the diamond texture while retaining its Poké Ball name/identity; disabling it restored normal resources. Before/after screenshots supplied. |
| Tomo bicycle service | PASS | MapKit registered `mapkit:bicycle`. Clicking BUY with exactly 500 BeastCoin granted one bicycle and left zero BC. Missing-item availability also matched the registry in an earlier actual run without MapKit. |
| Explicit BeastCoin isolation | PASS | Purchases changed BeastCoin while the independently configured HunterCoin balance remained 9000. The fixture made HunterCoin primary to catch incorrect fallback. |
| Zero/insufficient/exact/high balance | PASS | 0 and 19 BC rejected a 20 BC bundle; exactly 20 granted 16 Poké Balls and left 0; higher-balance purchases debited exactly 20. Updated balance appeared without closing the shop. |
| Full inventory | PASS | All available slots filled; purchase granted no Poké Balls and left 80 BC unchanged. |
| Grant failure and exact refund | PASS | Explicit QA-only post-grant fault injection exercised inventory rollback and refund. Balance returned to exactly 80 and no purchased items remained. No normal production fault injection is enabled. |
| Rapid double-click | PASS | Two rapid C2S requests produced one 16-ball grant and one 20 BC debit; the second request was rejected. |
| Malformed request / canonical pricing | PASS | Negative quantity and an unknown entry were rejected with no debit/grant. The C2S payload supplies shop, entry and quantity only; there is no client price field. |
| Data-driven default prices | PASS | The runtime fashion audit checked every reachable entry's category and 1–500 BC price. All explicit Ren items resolved. PokéMall baseline bundles, 500 BC bicycle and 500 BC Zacian Sword appeared in the real client. Owner overrides/reload are documented. |
| Stable pinned objective | PASS | Pin saved quest/objective/target identity on the server. The phone refreshed immediately and closing it already showed navigation, without reconnecting or reopening. |
| Live navigation | PASS | Actual screenshots show yaw-relative arrow changes and distance changes while moving. Re-placing Mara changed the resolved target to the placement store's new coordinates. |
| Unplaced/different-dimension targets | PASS | Unplaced target displayed unavailable. Moving the authored placement to the Nether produced the different-dimension state; returning it restored the live target. |
| Objective reset | PASS | Both `/cworld story objective reset` and `/cworldresetobjective` worked through non-OP player command packets and immediately cleared the side pin/resynced the main target. Commands are not shown in story copy. |
| Level-cap battle rejection | PASS | A level-16 party under cap 15 could not begin Mara's battle; the Pokémon's actual level stayed 16 and no victory flag was granted. |
| Trainer scaling | PASS | Actual battles logged player/trainer ace pairs 12/12 for Mara, 23/23 for Orin and 100/100 throughout campaign testing. Authored relative level gaps and clamping also passed unit tests. |
| Native faction basics | PASS | Normal phone requests created the player's native faction, rejected owner leave, and disbanded it. Faction phone views worked in both languages. |
| Optional SVFrame absence | PASS | Phone and gameplay ran with SVFrame integrations absent; native systems remained functional. |
| Town 1–7 progression | PASS | Actual Mara, Orin, Rook, Selene, Sixth Warden, all three Rocket grunts, Vex and Seventh Warden victories; Iris and Marlow interaction hooks advanced the ordered chain. |
| Town 8 qualifier and Tower lock | PASS | Battle Tower rejected access both before the circuit and after Seventh Warden. Harbour Marshal Liora confirmed the seven-town record; actual Captain Dorian victory completed the qualifier and unlocked Mina's Tower registration. |
| Battle Tower ordered trainers | PASS | Mina registration followed by actual Rowan → Nyx → Orion victories; Royal League registration unlocked afterward. |
| Royal League | PASS | Steward registration followed by actual Cassian → Seraph → Kael → Champion Aurelia victories, then School of Wolf unlock. |
| School of Wolf | PASS | Bran registration followed by actual Fen → Skoll → Hati → Master Vargan victories, with the TOBA record/meeting requirements granted by those gameplay hooks. |
| Story-spawned final encounter | PASS | After Wolf completion, the configured story service spawned exactly one mysterious actor. Ordinary interaction started its real Cobblemon battle; victory revealed TOBA and completed the main story. Before/after screenshots supplied. |
| Real process restart / reconnect | PASS | Both processes relaunched on the final JAR. Saved Mira anchor/pitch/yaw 72 recovered, the named reply/transcript remained saved, and balance 60 plus 16 purchased Poké Balls persisted. Four stages and clean client/server exit passed after the QA warm-up fix. |
| Missing BEconomy | PASS | The final JAR ran with BEconomy omitted. Ordinary Ren interaction opened the real disabled shop, the authoritative snapshot reported economy unavailable, and a purchase request granted no Poké Balls. All three stages and clean process exit passed. |

## Limits and unproven coverage

- **UNPROVEN — campaign difficulty and reward pacing:** legal, admin-prepared Mewtwo parties exercise battle transport and progression. They do not represent normal Pokémon acquisition or establish fair battle difficulty. Scaling is tested; campaign balance needs ordinary playtesting.
- **UNPROVEN — multiplayer faction invites, accept, role changes, scheduled island-war capture and combat:** the native implementations and persistence remain present; this pass exercised single-player create/owner-leave/disband and phone views, not a full multi-faction event.
- **UNPROVEN — installed SVFrameLib/SVFrameMMO integrations:** the exact ecosystem binaries were not supplied. Their absence fallback was exercised.
- **UNPROVEN — migration of the user's real production save:** no production world was supplied. Compatibility code preserves legacy flags/placements/messages/objective fallbacks, but a fresh QA world and its restart cannot establish migration of that particular save.
- **UNPROVEN — complete over-cap capture/natural-spawn/experience permutations:** the actual over-cap trainer rejection passed; this run did not test every level-cap hook.
- **UNPROVEN — every possible datapack reload/owner override, currency-backend outage or repeated rollback failure:** runtime full-inventory/refund/double-click paths and local transaction tests passed; rare external API failure combinations were not all injected in Minecraft.
- **UNPROVEN — audible feedback:** this headless client has no usable OpenAL output device. UI success feedback and screen state were exercised, but audible sound was not evaluated.

No medieval reference image was present in the supplied files; the original shop follows the requested wood/parchment/red-and-gold direction. Visual quality is subjective; actual screenshots are supplied for review. Endgame encounter coordinates must be configured by the map owner; the test fixture configured its own isolated encounter location and did not manually place `mysterious` as a persistent NPC.

The isolated offline client logs failed Mojang public-key requests and lack of an audio device; these did not prevent gameplay paths. Earlier interrupted or failed diagnostic runs are not represented as successful final-artifact tests.
