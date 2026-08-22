# Custom server binary integrations

Verified against the exact server JARs supplied for the 1.0.2 bridge pass.

## SVF All in One 0.1.5

Fabric mod id: `svf_all_in_one`.

Public API used by Lively:

- `com.svframe.svfallinone.SvfAllInOne.runtime()`
- `SvfRuntime.server()`
- `SvfRuntime.taiXiu()`
- `TaiXiuService.placeBet(UUID, TaiXiuSide, CurrencyId, BigInteger)`
- `TaiXiuSide.parse(String)`
- `CurrencyId.parse(String)`
- `TaiXiuService.BetReceipt.id()`

The external service owns validation, wager locking, database audit, withdrawals, refunds and settlement. Lively submits the request on the Minecraft server thread and returns the external receipt reference.

Safety boundary:

- Lively does not claim generic NPC support for the external wager API.
- SVF CobbleDollars withdrawals require an online player account.
- BeastCoin uses the BEconomy UUID API, but Lively still does not create or fund synthetic NPC player-economy accounts.
- HunterCoin is explicitly non-wagerable in SVF All in One.
- Autonomous NPC gambling remains in Lively's isolated virtual economy unless a future external API explicitly supports non-player actors without minting player currency.

## Cobblemon Tournament 1.1.3

Fabric mod id: `cobblemon_tournament`.

The supplied binary exposes `CobblemonTournamentMod.runtime()` and public tournament/betting/currency services. Lively recognizes the mod as an optional custom-server subsystem but does not route its economy through Tournament because Lively already binds BEconomy, CobbleDollars and Impactor directly.

## Cobblemon Showcase 1.1.0-hotfix.4

Fabric mod id: `cobblemon_showcase`.

The supplied binary exposes public showcase entity marker helpers through `ShowcaseRuntimeService`. It is treated as an optional custom-server subsystem and remains independent from Lively's core NPC runtime.

None of these JARs are bundled into Lively. Missing custom-server mods must never stop Lively Core or the Cobblemon Integration artifact from loading.
