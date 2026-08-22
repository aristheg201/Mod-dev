# Lively NPCs 1.1.0 — Living-world runtime completion

Target: Minecraft 1.21.1, Fabric Loader >= 0.18.4, Java 21, server-side primary.

1.1.0 intentionally does not require lifestyle animation. The release completes the behavioral, economic, social and justice loops so NPC actions have persistent server-authoritative consequences even when presentation remains vanilla/server-side.

## Justice

- Evidence-driven wanted levels and bounty pressure.
- Per-crime warrants with jurisdiction and expiry.
- Repeated review of the same crime is idempotent and cannot inflate bounty indefinitely.
- Police/officer/sheriff/guard NPC dispatch follows warrants rather than hidden perpetrator truth.
- Nearby warranted NPCs are arrested, navigation is stopped and AI is suspended while physically jailed.
- Jail/police/sheriff structures may expose `cell`, `holding`, `release`, `exit` or `entrance` semantic points.
- Court cases use evidence score, evidence count and alibi strength.
- Conviction creates fines/sentence/reputation consequences; unpaid fines become legal debt.
- Acquittal reopens the investigation instead of declaring a hidden ground-truth winner.
- Materially changed evidence can overturn a conviction and release the NPC.
- Wanted/warrant/custody/court state is versioned and persisted.
- Read-only admin surface: `/livelylaw status|wanted|warrants|custody|cases|inspect <uuid>` with `lively.admin.law`.

## Ambient society

- Nearby NPCs autonomously greet, chat, gossip, reconcile, argue and spend partner time.
- Rumors move through real carriers and social relationships.
- Arguments can escalate into semantic assault with nearby NPC witnesses and evidence.
- The encounter loop is bounded and pair-cooldown controlled; it is not an all-NPC per-tick scan.

## Households

- Partnered, engaged and married NPCs can inherit a missing home from their partner.
- Custody affects partner stress and relationship state.
- High gambling compulsion creates household financial/relationship strain.
- Trusted stable partners can transfer real internal-ledger funds to help with delinquent debt.
- Household memory is event-driven; no periodic `still together` memory spam.

## Businesses

- Configurable opening/closing minute.
- Configurable minimum staff and optional virtual-staff fallback.
- Actual owner/employee presence can be required for opening.
- Structure operational state gates business availability.
- Daily payroll, missed-pay social consequences and employee turnover.
- Rent and tax obligations flow to semantic treasury actors.
- Repeated payroll failure plus insufficient capital can mark a business bankrupt and close it; funded businesses may recover.
- Restocking is paid. A business buys from a configured supplier business or pays the external-supply sink before stock is added.
- The older duplicate free-restock path was removed.

Relevant business metadata:

- `business.open_minute`
- `business.close_minute`
- `business.minimum_staff`
- `business.virtual_staff`
- `business.wage`
- `business.rent`
- `business.tax_bps`
- `business.max_missed_payroll`
- `business.bankruptcy_balance`
- `business.recovery_balance`
- `business.restock_cost_ratio`
- `business.restock_batch_ratio`
- `business.supplier_business`

## Society pressure -> Story Director

Persistent society conditions can emit bounded world events which the existing Story Director can turn into arcs/quests:

- delinquent/collection debt -> `debt_crisis`
- bankrupt businesses -> `business_failure`
- high gambling compulsion -> `gambling_wave`
- active/high-risk warrant pressure -> `law_and_order`

Only two new society-pressure events can be started per society signal pulse and an already-active seed is not duplicated.

## Investigation integrity fixes

- Causal investigation no longer checks `Crime.perpetrator()` to decide whether a charge is correct.
- Society police patrol no longer follows `Crime.perpetrator()` directly.
- Charged cases remain under court authority instead of spawning new warrants every review cycle.
- The law runtime can therefore make evidence-driven mistakes and later correct them when evidence changes.

## Existing 1.0.x systems retained

- cognition, memory, beliefs, needs and utility decision making
- persistent PLAYER / VANILLA / EXTERNAL NPC bodies
- worker A* navigation, follow/escort/flee/schedule movement
- social reputation, rumors, romance and family
- crime/evidence/investigation
- player-facing real NPC shops
- provider-neutral BEconomy / CobbleDollars / Impactor routing
- gambling/debt/loan-shark behavior
- quests, Story Director, world events and chronicle
- SVF Waypoints quest projection
- Cobblemon Pokemon bodies, native trainer bodies, BattleAI and world awareness
- optional SVF All-in-One Tai Xiu bridge
- optional Cobblemon Tournament / Showcase recognition
- authority/security restrictions and bounded/async persistence design

## Deployment boundary

Automated release gates cover Java tests/remap, dedicated Core boot, production-layout Cobblemon boot, Mega Showdown boot, native trainer provisioning and JAR integrity. A long-duration soak with the complete live production world and every optional custom binary remains deployment validation.
