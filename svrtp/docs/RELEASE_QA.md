# SVRTP 1.0.0 release QA

Server-only Fabric 1.21.1 / Java 21. `/rtp` opens a native chest selection menu; destination selection opens payment choices of 5 BeastCoin or 50,000 CobbleDollars. Clients need the distributed resource pack for the pagoda skin, but no SVRTP client mod.

## Observed internal results

The actual remapped server JAR was tested with real graphical Minecraft clients, LuckPerms, BEconomy 1.5, CobbleDollars 2.0.0 Beta 6.1, Cobblemon 1.8.1, Chunky 1.4.23 and ChunkyBorder 1.2.18. Dedicated non-OP offline accounts were used. Destination fixtures used explicitly authored solid platforms and a custom `minigamedim:minigame` datapack dimension. SVRTP itself created no platform and generated no candidate terrain.

| Test | Result | Evidence / qualification |
|---|---|---|
| Resource native menu and 5 BeastCoin charge | PASS | Actual transfer into `minigamedim:minigame`, wallet delta 5 |
| Cooldown | PASS | Repeated request leaves wallet unchanged |
| Nether and 50,000 CobbleDollars | PASS | Actual dimension transfer and wallet delta |
| End safe landing and payment | PASS | Actual supported island fixture; not just an air coordinate |
| Edited Resource mapping after reload | PASS | Resource alias actually transferred to Nether |
| Unknown dimension | PASS | No transfer or charge |
| LuckPerms-denied non-OP | PASS | Destination rejected without charge |
| Both vanilla and native ChunkyBorder restrictions | PASS | All outside-border candidates rejected; no charge or movement |
| Rapid menu clicks and commands | PASS | One job, one fee |
| OP use | PASS | Actual Resource transfer using CobbleDollars |
| Delayed unsafe post-transfer correction | PASS | QA-only displacement injection; next tick restores safe origin and refunds exactly, health unchanged |
| Insufficient BeastCoin | PASS | No charge or transfer |
| Unloaded pregenerated candidate | PASS | Candidate chunk absent from live chunk lookup before request; real async FULL neighbourhood reads, actual transfer; `asyncLoads=7`, `ioPending=0`, latency 1,133 ms in this fixture |
| Hazard, headroom, void and border checks | PASS | Runtime block/collision checks in actual worlds; not all naturally generated biomes |
| GUI scales 2 and 3 | PASS | Genuine screenshots of the native menu and bitmap resource-pack skin |
| Config and journal automated tests | PASS | Five JUnit cases; not gameplay evidence |
| Two simultaneous genuine clients | NOT TESTED | Earlier attempts failed in fixture preparation; separate final-run results, if available, accompany this report |
| Active native Cobblemon battle | NOT TESTED | Guard exists; a successful final native-battle fixture must be logged before PASS |
| Restart/reconnect durable cooldown | NOT TESTED | Journal unit tests are not a real restart |
| Pending disconnect / external dimension change | NOT TESTED | Cleanup implemented; no completed end-to-end case logged |
| Production normal-use tick impact | NOT TESTED | No deployed production profile or matched-load before/after comparison |
| Real production OP/non-OP destinations | NOT TESTED | Production game TCP unavailable; internal tests waived human assistance |

The 13 core run completed with normal dedicated-server shutdown. Failed concurrent fixture runs are retained: one client connected before its atlas was ready; another used a neighbourhood that was not FULL on disk and correctly failed its searches without fees. That second disposable test server later spun in vanilla chunk-save shutdown and was stopped locally after evidence capture. The cause is unresolved; do not treat it as successful restart evidence or attribute it to a production mod without isolation. One RAM-limit event occurred when too many internal clients, servers and builds overlapped; production was unaffected.

## Safety and operational limits

Default Resource is unconfigured. The supplied production example selects `minigamedim:minigame`; admins can change any of the three IDs. Default optional generation is disabled, and `allowChunkGeneration=true` is rejected rather than allowing unbounded generation. Destination annuli require separately pregenerated FULL neighbourhoods. Cold vanilla End arena scanning is rejected until prepared outside peak usage.

ChunkyBorder support is explicitly limited to the audited installed version pair. Native shape checks complement vanilla borders; arrival is verified immediately and over the following second. Compatibility with every other protection mod is unverified.

Economy APIs and the local journal cannot form one shared atomic transaction. A crash between them may require manual receipt reconciliation; ambiguous receipts block further charges rather than risking duplicate credits. Journal durability writes are small but synchronous, so production disk latency remains a measurement requirement. No production stress test or recurring-stutter-free claim is made.

Configuration, build dependencies, permission nodes, cancellation, rollback and resource-pack distribution are documented in `README.md`. The separate QA-driver JAR is not part of the release JAR and must never be installed on production.
