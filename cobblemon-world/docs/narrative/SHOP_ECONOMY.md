# Shops and BeastCoin economy

Ren, Elle and Tomo use a dedicated ShopScreen. NPC interaction opens an authoritative snapshot; purchases send only shop ID, entry ID and bundle count. Prices and item grants are resolved on the server. Actual ItemStack rendering supplies item names, models, tooltips and resource-pack overrides.

| Bundle | BeastCoin |
|---|---:|
| 16 Poké Balls | 20 |
| 8 Great Balls | 30 |
| 8 Ultra Balls | 45 |
| 8 Potions | 16 |
| 6 Super Potions | 24 |
| 4 Revives | 40 |
| Bicycle | 500 |

Fashion enumerates both `cobblemonarmory` and `cobblemonarmors` item namespaces at reload. Runtime startup with the supplied releases observed 262 eligible entries; that is an observation, not a catalog-size constant. Armory’s internal creative marker is excluded. The runtime report records the independent registry audit, category reachability, scrolling and purchase evidence.

Data rules assign materials 25–75 BC, accessories 70, ordinary armor 90/130/110/90, ordinary weapons 220, shiny armor 180/260/220/180, Rayquaza armor 250/320/290/250, shiny Rayquaza 360/450/410/360, and Zacian Sword 500. The loader rejects every default or override price outside 1–500. Shiny items can belong to both their equipment category and Shiny.

Definitions are in `data/cobblemonworld/shops/*.json`. A datapack or complete `config/cobblemonworld/shops/<id>.json` override lets owners change prices and rules without editing the renderer. Reload rebuilds catalogs and invalidates existing sessions.

BEconomy 1.5 is called through its API with the BeastCoin currency type explicitly resolved. HunterCoin remains separate. Missing BEconomy, a missing BeastCoin account or an uncertain transaction fails closed. Sessions are NPC/distance-bound and expire; requests validate quantity, access, canonical entry and registry availability. Purchases serialize on the server thread with an eight-tick repeat guard. Inventory preparation reserves capacity before debit. A failed grant rolls back inventory and verifies an exact currency refund; an irreconcilable balance disables purchasing and logs the affected transaction.

Regular substantial side chains pay 20 BeastCoin once. The optional scam costs 10, gives a real dead bush, and refunds that loss separately when the investigation finishes. Reward credits have a persisted entitlement ledger; an uncertain external API credit is held for reconciliation. This pass does not establish ordinary player reward pacing merely by finishing transport tests with supplied fixtures.
