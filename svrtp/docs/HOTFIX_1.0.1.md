# SVRTP 1.0.1 and the production spawn-rule hotspot

## Source-position rejection

SVRTP 1.0.0 reused `SafeLanding.valid()` for the player's source and rollback. That function is deliberately strict for a random destination: a 5x5 full-block dry surface, three blocks of clearance, and an interior coordinate in a loaded chunk. Consequently, ordinary dry ground at chunk edges, slabs, carpets, low ceilings and uneven surrounding terrain could never start RTP.

1.0.1 introduces `SafeLanding.restorable()`. It checks the exact translated player bounding box, loaded chunks, effective borders, fluids/hazards, real collision support immediately beneath the feet and body clearance. It does not load chunks. Random landing validation remains strict. Rollback uses the original fractional coordinates instead of rounding a slab/carpet position to an integer Y.

The remapped server and graphical vanilla-container client completed six real paid teleports from: a chunk edge, a slab, carpet, a body crossing a chunk boundary, two-block headroom, and an uneven neighbouring floor. Each transferred to the configured Resource dimension and charged exactly 5 BeastCoin. Native block fixtures reject lava, water, unsupported air, body collisions and an unloaded/out-of-border source. The QA driver is excluded from the production JAR.

The additional 13-case graphical-client regression run also passed: Resource/Nether/End transfers, cooldown, editable dimension mapping, unknown dimension, permission denial, border/search failure, repeated-click protection, OP use, insufficient funds, actual unloaded FULL chunk loading, and delayed unsafe-position correction. The correction case starts on a slab at Y=119.5; it returns to that exact fractional height without health loss and fully refunds the charge. Async loading reports seven loaded chunks and zero pending I/O at completion. These are local runtime results, not production acceptance.

## Production lag evidence

The user supplied https://spark.lucko.me/Ql7S9RY5qe. This snapshot reports a 10,421.248 ms maximum tick, 15.282 TPS over five minutes, 2.614 ms median MSPT and 5.043 ms P95. A sampled window contains one player, 19 entities and 274 chunks. Main tick work is sampled for 18,760 ms, including 14,060 ms in Cobblemon's `FilterRuleComponent.affectSpawnable`: approximately 75% of that tick path. This is inclusive sampling time, not a separate wall-clock timer for every stall.

Call path: `PlayerSpawner.tick -> BasicSpawner.getMatchingSpawns -> SpawnablePosition.preFilter -> FilterRuleComponent.affectSpawnable -> ExpressionSpawnDetailSelector -> MoLang BooleanOrExpression`.

The installed `world/datapacks/FakemonOnly-Overworld-1.21.1.zip` contains `data/cobblemon/spawn_rules/svf_fakemon_only_overworld.json`: 65 filters with 1,025 exact official-species comparisons. Native Cobblemon evaluates each detail selector before its dimension selector. Thus the blacklist repeatedly creates/evaluates Pokémon-property query values for candidate spawn details, including outside Overworld.

The complete existing capture was stopped and preserved at https://spark.lucko.me/RLifc99bmt. It still contains the same dominant spawn-filter work. PebblesCrates' patched particle callback contributes only 60 ms in the supplied short snapshot and 1,048 ms in the complete capture. CompanionBonds synchronous JSON serialization also appears (1,620 ms in the short snapshot), but is smaller than the spawn-filter path. These captures do not prove every individual stall has the same cause. SVRTP was generally rejecting requests at the source check; this does not establish paid-RTP performance on production.

## Datapack fix

`tools/optimize_fakemon_rule.py` validates the original exact list and combines it into one native filter. The native constant detail selector accepts all details; the unchanged dimension selector runs first. Only inside Overworld does `allow` evaluate one delimiter-bounded `q.is_included()` lookup. Exact names remain exact: partial names and namespaced/custom variants are not newly blocked. The Alpha x0.5 rule and all other ZIP entries remain byte-for-byte unchanged.

Actual Minecraft/Cobblemon registry loading and MoLang evaluation checked all 1,025 official names and six non-members. Results match the original blacklist. This native-engine evaluation measured approximately 1,026.5 ms for the original selectors and 10.9 ms for the replacement in one internal comparison. This is not a production before/after performance claim or a natural-spawn gameplay test.

The first internal harness attempt used MoLang's query-context map instead of the native filter's environment variable binding and failed. The fixture was corrected to match `FilterRuleComponent`; the replacement datapack then passed. Failed evidence is retained.

## Install and rollback

The user chose to deploy/test personally. No production files, gamerules, player data or JARs were changed, and production was not restarted/reloaded by Codex.

* Move a backup of the original datapack **outside** `world/datapacks`. Rename the optimized ZIP to the original filename and replace it. Do not load both copies. Run `/reload`; check for resource/spawn-rule errors. Keep the same player load and location, then capture `spark profiler start --timeout 120` and share the resulting link. Production after-fix performance is unverified until that capture exists.
* During the user's maintenance window, replace only the server's SVRTP 1.0.0 JAR with `svrtp-1.0.1-fabric-1.21.1.jar`; do not keep both. Normal restart is required to load a different JAR. No client SVRTP JAR is required. Existing config, journal, prices and resource pack remain compatible. Resource GUI needs `resourcePackGui=true` and the previously supplied merged pack.
* Roll back by restoring each backed-up file and reloading the datapack/restarting for the JAR. Preserve `config/svrtp.json` and `<world>/svrtp/journal.json`; never delete transaction receipts to resolve an unknown payment.

Remaining validation: live production RTP, natural Fakemon spawn behavior, representative after-fix MSPT/GC/disk metrics and the separate CompanionBonds persistence hotspot.
