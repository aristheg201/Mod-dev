# Card Worlds performance implementation

`patch.py` is applied after the production overrides. Source restoration followed by
`python3 source-overrides/apply.py` reproduces the implementation; generated `src`
files are not a second source of truth.

Mutable duels, legality checks, sequential reverse-order Chain resolution, world,
Cobblemon, networking commits and BEconomy calls remain on the Minecraft owner
thread. Immutable AI inputs and serialization inputs go to owned CPU and
serialization pools. UI database reads use bounded, independent SQLite WAL read
leases on the IO pool; successful authoritative SQL commits remain durable and
synchronous. Preference writes and complete history chunks use the IO pipeline.

The defaults are 4 CPU workers at most, 1 IO worker and 2 serialization workers,
with queues of 128/64/64. Saturation uses explicit synchronous backpressure rather
than losing work. A duel retains one actual AI computation until it completes;
stale results are discarded before commit. Serialization retains one active and
one merged input per connected player. Notification accumulation applies
backpressure after 64 updates. Shutdown drains persistence and owned pools.

Trigger lookup changes from scanning every piece/effect per event to querying
registered sources for that event, in canonical source order. Incremental dirty
source updates maintain continuous effects, stages and operation indexes. Zone
indexes narrow target candidates without removing candidates or legality checks.
Turn/event delayed buckets replace scans of all delayed effects. The former silent
64-delayed-effect drop is removed; a focused test resolves all 72 queued effects.

Typed effect data and static flags are compiled per immutable catalog generation.
An active duel retains its own definitions across reloads. The `snapshot_v2`
protocol sends static content once per connection/generation and reuses unchanged
database UI data. Revision guards reject stale worker outputs; mismatched client
content/UI keys request a full recovery. Final duel views and ordered presentation
cues survive coalescing. This protocol requires matching client/server releases.

The Collection and Deck grids cache filtering/sorting and calculate visible row
ranges. The booster list also iterates only its visible rows. Rules, names and model preparation caches are bounded and invalidate on
language/resource reload. No catalog entries or visual features are removed.

Reward receipts use a disk-indexed unique SQLite journal with FULL durable
reservation and a streaming migration of legacy receipts. BEconomy's balance
store cannot share the receipt transaction: an uncertain external API outcome
remains explicitly `PENDING` and requires reconciliation; it is never blindly
paid again. Pull receipts retain CardStore transaction safety and serialize the
receipt/debit/commit/refund sequence. These APIs never run on arbitrary workers.

Enable aggregate profiling in `config/svarcade-tcg/performance.json` with
`perfMetrics: true` (or the `cardworlds.perf` JVM property). Admins can inspect
`/cardworlds perf` and reset samples with `/cardworlds perf reset`. Metrics have
fixed-size sample rings and produce no per-tick log spam.

## Verification

After restoring/applying source and applying `qa/focused.gradle` to Gradle:

```sh
python3 source-overrides/duel-realm/production-visual/performance/verify.py
gradle --no-daemon test remapJar remapFocusedQaJar
gradle --no-daemon cardWorldsLoad -Pduels=8 -PloadTicks=120
gradle --no-daemon cardWorldsLoad -Pduels=16 -PloadTicks=120
gradle --no-daemon cardWorldsLoad -Pduels=32 -PloadTicks=120
```

`BaselineDuel`, `BaselineAiPlanner` and `BaselineBenchPlanner` preserve the engine
and planner generated from parent HEAD `9dac5e90ccf98bf9e32e58db99ee6d93f97f2ff8`.
Tests compare every candidate, validation result and authoritative state after
every command across eight seeded scripts. Race tests cover obsolete AI,
disconnect/end, late serialization, resource generations, bounded queues,
worker failures, duplicate economy calls and database-reader isolation.

The headless harness reads the installed Cobblemon species JSON and exercises
populated 40-card decks, multiple effects, normal/hard AI, Chains, continuous
effects and counters. Every action round commits one action per duel. It compares
final baseline/async state and records owner-thread **CPU time**, worker time,
latencies, queue depths, allocations and actual encoded snapshot sizes.
Completion barriers belong only to the harness. These measurements demonstrate
Card Worlds' contribution to a tick budget; they are not measured Minecraft TPS.
Timing evidence is recorded rather than used as a machine-dependent CI gate.

The single focused workflow also boots the dedicated server, proves BEconomy
authority/reward rules/effect-cost rules, checks clean shutdown, and captures only
the existing three UI screenshots and all 14 pack previews with the complete
production list retained. Pack QA scrolls to and selects each banner, checks its
preview/rates/currency descriptors, rejects model/rendering errors, and records
one extra image per pack. It never executes a purchase or enters the full suite.
It uploads benchmark JSON, JUnit results, server/client logs, production JAR,
SHA256, built effects and screenshots together.
