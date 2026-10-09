# 0.3.2 QA — actual observations

These results refer to internal real Minecraft gameplay, unless explicitly marked production. Native battle results, wallet/inventory checks, server state and real screen captures accompany the release bundle. Fixture setup is not ordinary player progression and is described in `RELEASE_0.3.2.md`.

| Requirement | Status | Evidence / limit |
|---|---|---|
| Brand-new player meets Hale and starts story | PASS | Opening client/server run, non-OP account |
| Skip all Ren purchases and reach Mara | PASS | Opening stages progress without buying |
| Legal party starts actual Mara trainer battle | PASS | Native trainer registry and real client battle |
| Lose, retry and win Mara | PASS | Native loss and victory outcomes; no forged result |
| Victory advances story | PASS | `mara_first` followed by investigation/Unknown/Hale |
| Complete every main stage | PASS | Combined persisted campaign runs record all 69 stage IDs; real TOBA battle victory and epilogue; not a single uninterrupted difficulty-balanced playthrough |
| Level-cap rejection feedback through conversation | PASS | Real client selects Mara challenge with a level-16 party and cap 15; localized server reason remains visible and no battle starts |
| Disconnect mid-conversation and resume | NOT TESTED | Local close/reopen and saved campaign resume have evidence, but are not this network-disconnect case |
| Restart/reconnect retained campaign progress | PASS | Same saved account resumed across separate dedicated-server/client processes without reset |
| Complete at least one side chain | PASS | Actual talk, inspection, delivery and native battle stages; full 45-chain coverage is recorded separately in the log-derived coverage file |
| Pin/switch/reset objectives; changing NPC position | PASS | Services run validates server navigation identity and reset actions |
| Direction/distance and other dimension | PASS | Actual HUD captures and target dimension change |
| Ren transaction and server-backed inventory | PASS | Exact funds, insufficient funds, quantity/entry validation, inventory-full rejection, success |
| Repeated old-revision shop click | PASS | One debit/delivery for the duplicate request |
| Failed grant refund | PASS | Controlled failure injection; native economy balance restored |
| Elle complete available catalog | PASS | 262 registered Armory/Armors items enumerated; registry equality, categories and scrolling/scales captured |
| Actual purchase from Elle | PASS | Real custom shop click delivers registered Zacian sword, with exact server-authoritative BeastCoin debit; same run includes actual SVFrameLib/MMO JARs and LuckPerms |
| Tomo actual bicycle purchase | PASS | Registered MapKit bicycle delivered with exact debit |
| Mira actual heal/service | PASS | Native Pokémon health restored; custom conversation service action |
| Phone apps and native factions | PASS | Trainer Card, Objective, Story, Level Cap, Badges, Contacts, League, Faction; actual create/owner-leave rejection/disband |
| Phone messages/reply/side-quest actions | PASS | Actual localized messages, reply history, quest pin; reply branches tested separately |
| NPC placement and upright anchor | PASS | Actual physical entity/position/pitch and displacement recovery |
| NPC recovery from placement-store reload | PASS | Entity discarded, persisted store reloaded, one correct recovered anchor |
| All authored NPCs survive server restart | NOT TESTED | Authored participants recovered in campaign runs; no comprehensive 63-anchor restart census |
| Schema-1 0.3.1 save migrates in runtime | PASS | Controlled schema-1 Ren fixture saved, reloaded and migrated by the actual server; histories, claims, flags and side quests retained, no currency reward; three unit cases also PASS. Actual production accounts remain NOT TESTED |
| Schema-0 0.3.0 save migration in runtime | NOT TESTED | Legacy inference unit test PASS; no native schema-0 fixture run |
| Two players progress independently | NOT TESTED | One campaign QA account used; RTP two-client cases are not story independence |
| Legendary claim replay | PASS | Server repeats actual claim checks and verifies exactly one of each Legendary in native storage; genuine PC GUI selects both |
| Every possible reward replay | NOT TESTED | Refusal graph/unit checks PASS; exhaustive runtime replay of all reward types is incomplete |
| Vietnamese display and UI scales | PASS | Actual screens, dialogue player/NPC replies, shop scales 2/3 and Phone locale captures |
| No raw keys anywhere in gameplay | NOT TESTED | Complete authored localization/reference validation PASS, sampled screenshots clean; exhaustive UI review is incomplete |
| Full dialogue coherence editorial review | NOT TESTED | Vietnamese authored graphs/transcripts rewritten; automated full-graph validation PASS, not a claim of exhaustive human review |
| All authored narrative side chains | PASS | All 45 chains / 241 optional stages observed across saved runtime runs; includes native Legendary claims and PC selection, capture/heal, collect, postgame rematch and Ren optional purchase. Supplies/placement fixtures are documented |
| Every NPC and every possible service/UI action | NOT TESTED | Full narrative stage coverage is not an exhaustive test of all unused NPC/menu branches |
| Dedicated server/client initialization | PASS | Remapped JAR with exact Cobblemon and supported native dependencies |
| SVFrameLib 1.7.1 / SVFrameMMO 1.13.1 opening compatibility | PASS | Supplied HP-fix JARs plus LuckPerms: complete 11-step opening, actual Mara loss/retry/victory and investigation; local default configs, not production data |
| Complete production modpack compatibility | NOT TESTED | Entire 213-mod pack and production configuration not run end-to-end; live deployment not performed |
| Production gameplay acceptance | NOT TESTED | Panel/profiling access worked, raw game TCP unavailable; user requested internal testing |

A separate real restart run completed **6 stages**, verifying NPC anchor recovery, saved dialogue, currency/inventory, actual schema-1 save migration and over-cap UI rejection. Services run completed **93 stages** with `CWORLD_PROD_QA_FINISHED`, followed by normal client and server shutdown. All 19 JUnit tests passed; narrative/static data validation passed. These are separate evidence categories.

Two meaningful failed campaign runs were retained: native loss followed by a QA retry-token bug, and a colocated-POI interaction deadlock. The first was a harness defect, repaired with distinct retry tokens; the second was a gameplay defect in `PoiStore`, repaired and replayed successfully. The final encounter later won through the native registry. Old failed logs are not erased.

The source includes historical 0.3.1 tests and documents. Their results must not be counted as 0.3.2 evidence. Report `NOT TESTED` rather than infer success from a method existing or a build passing.
