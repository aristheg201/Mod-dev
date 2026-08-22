# Lively NPCs by SVFrame Studio

Two server-side Fabric mods for Minecraft 1.21.1 / Java 21.

- **Lively NPCs by SVFrame Studio** (`livelynpcs`): persistent offline living-world runtime for autonomous NPC cognition, society, economy, law, quests and world consequences. No external LLM or cloud AI dependency.
- **Lively NPCs: Cobblemon Integration by SVFrame Studio** (`livelynpcs_integration`): requires Lively Core + Cobblemon 1.7.x and provides native Cobblemon Pokemon/NPC bodies, trainer battle AI, Cobblemon world awareness, and optional server-ecosystem adapters.

Current release version: **1.1.0**.

## 1.1.0 living-world completion pass

1.1.0 deliberately does **not** add lifestyle animation. It completes the runtime behavior and consequence loops behind the NPC society instead.

### Evidence-driven law and justice

- Persistent wanted levels, bounties, per-crime warrants, jurisdiction, police dispatch, NPC arrest, custody, semantic jail facilities, court hearings, fines, sentences, release and conviction review.
- Warrants and verdicts use investigation evidence, suspect score and alibi strength. The law runtime never reads the hidden perpetrator field as an oracle, so mistaken charges and overturned convictions remain possible.
- Multiple crimes/warrants are isolated. Re-reviewing the same crime is idempotent and cannot inflate bounty forever.
- Court fines use the internal ledger; unpaid balances become legal debt rather than disappearing.
- `/livelylaw status|wanted|warrants|custody|cases|inspect <uuid>` provides a read-only admin view with `lively.admin.law`.
- Justice state persists in the versioned society state store with backward-compatible loading from the 1.0.x schema.

### Ambient society and households

- Nearby NPCs autonomously greet, talk, gossip, reconcile, argue and spend partner time using actual social/romance state.
- Rumors propagate through real carriers instead of a global broadcast.
- Arguments can escalate into semantic assault cases with nearby NPC witnesses and evidence.
- Partnered/engaged/married NPCs form household consequences: shared home fallback, custody stress, gambling-related relationship strain and real ledger-backed financial support for delinquent debt.

### Businesses that have to function

- Businesses obey configured opening hours and semantic structure state.
- Shops can require actual owner/employee presence before opening.
- Payroll is daily, with missed-pay consequences and employee turnover.
- Rent/tax obligations feed semantic treasuries.
- Repeated payroll failure plus insufficient capital can close a business; funded businesses can recover.
- Restock is paid. It buys from a configured supplier business or pays an external-supply sink before stock appears. Free duplicate restocking from the older causal simulation path was removed.

### Society -> story consequences

Persistent pressure can now become bounded world events consumed by the existing Story Director:

- delinquent/collection debt -> `debt_crisis`
- business bankruptcy -> `business_failure`
- high gambling compulsion -> `gambling_wave`
- high wanted/warrant pressure -> `law_and_order`

These signals can continue into world events, story arcs and generated quests without lifestyle animation being required.

## 1.0.2 custom-server bridges retained

- Verified and integrated the public Tai Xiu API from `SVF-All-in-One 0.1.5` through an optional reflective `GamblingBridge`.
- External Tai Xiu wagers remain owned and settled by SVF All in One; Lively never mirrors or mints player currency into NPC accounts.
- `CobblemonTournament 1.1.3` and `CobblemonShowcase 1.1.0-hotfix.4` remain optional custom-server subsystems.
- Lively hooks BEconomy, CobbleDollars and Impactor directly through provider-neutral routing.

## Architecture rules

1. AI workers only reason over immutable snapshots.
2. AI produces typed proposals; the server-authoritative Authority Layer executes or rejects them.
3. Dialogue is rendered in chat but isolated from normal player chat while a session owns input.
4. Combat reasoning is part of the Lively AI core; game-specific battle state/action mapping belongs in Integration.
5. External integrations must not leak implementation types into the core API.
6. SVF Waypoints is player-facing quest navigation; NPC pathfinding remains Lively's own bounded navigation runtime.
7. Lifestyle animation is presentation, not a requirement for simulation correctness. State, money, evidence, navigation and consequences remain authoritative without it.

Development branch: `feature/lively-npcs`.
