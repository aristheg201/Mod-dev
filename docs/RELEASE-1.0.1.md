# Lively NPCs 1.0.1 - Society Simulation

## Scope

1.0.1 turns the existing social, economy, romance, crime and schedule engines into one causal society loop instead of a collection of disconnected features.

An NPC can now, depending on personality, needs, relationships, money and world state:

- go to work, return home and sleep;
- buy a meal from an NPC restaurant;
- go drinking at a bar/tavern and reduce stress/entertainment pressure;
- spend time with a current romantic partner and create shared memories;
- gamble at a casino/gambling-den business;
- build gambling exposure/compulsion from repeated play;
- borrow money from a loan-shark/money-lender business when desperate;
- accrue interest and become delinquent;
- repay debt from actual simulated funds;
- be followed by a debt collector and suffer social/crime consequences from illegal collection;
- commit economic crime under high financial pressure/criminality;
- be investigated/pursued by police-role NPCs;
- remember those events so later social/dialogue/story systems can react to them.

The physical-world safety invariant is unchanged. Society simulation does not break/place terrain, steal physical player inventory, execute arbitrary commands, or invent unvalidated server actions.

## Player-facing NPC shops

Businesses created from NPC metadata can now expose real player purchases. The purchase flow is server-authoritative:

1. Resolve the NPC-owned business and access policy.
2. Resolve the configured currency through the multi-provider economy router.
3. Validate business state, stock and the quoted dynamic price.
4. Withdraw player funds asynchronously through the selected economy provider.
5. Revalidate stock and price on the Minecraft server thread.
6. Commit the Lively business transaction, reduce stock and grant the real item stack.
7. Refund through the same provider if authoritative state changed before commit.

Interacting with a non-native Lively merchant shows its stock with clickable `[1]`, `[8]` and `[64]` purchase amounts. Commands are also available:

```text
/livelyshop open <npc-id-or-name>
/livelyshop buy <business-uuid> <item-id> <quantity>
/livelyshop providers
/livelyshop route <currency> <provider>
```

The last two commands require operator-level administration. Runtime route changes are not automatically written to disk.

## Multi-provider economy

Core exposes an asynchronous provider-neutral economy contract and `EconomyRouter`. Cobblemon Integration optionally registers providers when their mods are installed:

- `beconomy` - BEconomy 1.5 bridge, executed on a dedicated worker so synchronous provider calls do not block the Minecraft thread;
- `cobbledollars` - CobbleDollars 2.0 Beta 5.1, using its real `CobbleDollarsPlayer` account methods on the server thread and deliberately avoiding offline account-file I/O from runtime tick paths;
- `impactor` - Impactor 5.3.5, using its real asynchronous `EconomyService` / `Account` API and registered multi-currency keys.

Default generated routing file:

```properties
route.cobbledollar=cobbledollars
route.cobbledollars=cobbledollars
route.cd=cobbledollars
route.beastcoin=auto
route.huntercoin=auto
```

File location:

```text
config/livelynpcs/economy.properties
```

`auto` removes an explicit route and lets the router select a loaded provider that reports support for the currency. A provider can also be addressed explicitly with a provider-prefixed currency such as `beconomy:beastcoin`.

The old single `EconomyBridge` remains for compatibility with existing integrations, but new 1.0.1 commerce uses the multi-provider router.

## Configuring a normal shop

Create an NPC, assign its workplace/location as usual, then add business metadata. Example commands:

```text
/lively npc set <npc-id> meta business.name Pokemart
/lively npc set <npc-id> meta business.kind shop
/lively npc set <npc-id> meta business.currency cobbledollar
/lively npc set <npc-id> meta business.initial_balance 500000
/lively npc set <npc-id> meta business.stock.minecraft:bread 64,64,20,0.50,0.50
/lively npc set <npc-id> meta business.stock.minecraft:golden_apple 8,16,500,0.65,0.35
```

Stock format is:

```text
quantity,targetQuantity,basePrice,demand,supply
```

The final player price is still affected by Lively's scarcity, demand and supply model.

## Bars and restaurants

Business kinds used by society simulation include:

```text
restaurant
food
bar
tavern
pub
```

Optional metadata copied into business facts:

```text
business.meal_price
business.drink_price
business.currency
```

Example:

```text
/lively npc set <npc-id> meta business.kind tavern
/lively npc set <npc-id> meta business.drink_price 35
/lively npc set <npc-id> meta business.currency cobbledollar
```

NPCs still pay from the internal living-world wallet. External player currencies are used only when a player-facing transaction crosses the integration boundary.

## Gambling businesses

Recognized society business kinds:

```text
casino
gambling
gambling_den
taixiu
tai_xiu
```

Optional metadata:

```text
business.max_bet
business.house_edge
business.game
business.currency
```

Relevant NPC traits/metadata include:

```text
gambling_affinity
society.gambling_affinity
```

Repeated gambling updates persistent exposure/compulsion state instead of assigning an addiction flag from one random roll.

Core also exposes `GamblingBridge` for a real external minigame adapter. The archived server files supplied for 1.0.1 contain casino/roulette configuration but not the actual Tai Xiu implementation JAR, so this release deliberately does not fabricate a binary-specific Tai Xiu reflection adapter. Internal NPC gambling is functional; an external adapter can bind once the exact mod/API is available.

## Loan sharks and debt collection

Recognized lender business kinds:

```text
loan_shark
money_lender
```

Optional metadata:

```text
business.loan_amount
business.interest_bps
```

Debt contracts persist creditor, debtor, principal, outstanding balance, interest, due time, legal/illegal state and lifecycle status:

```text
ACTIVE
DELINQUENT
COLLECTION
REPAID
DEFAULTED
FORGIVEN
```

NPCs with a role containing `collector`, `loan shark`, `cho vay`, or metadata `society.debt_collector=true` can pursue delinquent debtors. Aggressive illegal collection can create an evidence-backed semantic assault/extortion case rather than silently changing reputation with no cause.

## Police and crime

NPCs with police-like roles (`police`, `officer`, `sheriff`, `cảnh sát`) or `society.police=true` participate in law-enforcement behavior. They react to open/investigating crimes, pursue known NPC perpetrators through Lively navigation and record dispatch memories.

Economic crime is motive-driven rather than a fixed criminal NPC loop. High criminality plus financial pressure can produce business theft, which creates both a real internal simulated money transfer and a CrimeEngine case with evidence.

Useful inputs:

```text
/lively npc set <npc-id> trait criminality 0.85
/lively npc set <npc-id> need financial_stress 0.90
/lively npc set <npc-id> meta society.police true
/lively npc set <npc-id> meta society.debt_collector true
```

## Romantic/social activity

Existing RomanceEngine bonds now affect routine behavior. A dating/partnered/engaged/married NPC can seek its partner during leisure time, navigate toward them, create a shared memory and update the underlying social relationship.

This is intentionally social/affectionate gameplay behavior. Lively does not add explicit sexual content.

## Persistence and performance

1.0.1 adds a separate checksummed society state file:

```text
<world>/livelynpcs/state/society.json
```

It stores debt and gambling state using atomic replacement, backup fallback and an ordered daemon I/O worker. Autosave is off the tick thread and shutdown performs a durability flush.

The society loop runs every 200 ticks and processes at most 96 NPCs per pulse using a rotating cursor. It does not scan every NPC every tick. Navigation remains bounded and uses the existing main-thread sampling / worker A* pipeline.
