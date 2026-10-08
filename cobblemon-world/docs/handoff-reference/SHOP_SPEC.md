# Custom Shop HUD + BeastCoin specification

## Rejected implementations

The following are explicitly rejected:

- clickable chat as the primary shop UI;
- telling players to type a purchase command;
- `tellraw`-built catalog panels;
- vanilla chest GUI as the visual target;
- fake item icons that do not render the actual `ItemStack` model;
- fake `PhoneSnapshot` markers as final architecture;
- client-authoritative price/item granting;
- prices in the tens/hundreds of thousands of BeastCoin.

## Visual reference

See `reference/medieval-shop-ui-reference.png`.

Use the design language:

- centered fantasy/pixel-art shop board;
- parchment panels;
- dark wood/brown frame;
- gold/red accents;
- category tabs;
- item grid;
- large selected-item/detail area;
- obvious back/close/buy affordances;
- visible currency.

Create original assets that fit CobblemonWorld. Do not copy the source image's art verbatim.

## Layout behavior

At normal GUI scale:

- merchant/title area;
- category strip/list;
- 3+ columns of item cards depending on available width;
- item card: rendered `ItemStack`, display name, optional count, price;
- selected card visual state;
- detail panel with full item name/category/price;
- BUY button;
- current BeastCoin balance;
- scroll bar + mouse wheel;
- disabled BUY state for insufficient funds/service unavailable.

For 200+ items, category filter and scroll are mandatory. A text search is optional but useful.

## Full Cobblemon Armory catalog

Reference dependency: `cobblemonarmory-1.5.4-fabric-1.21.1.jar`.

The current static audit generated 204 catalog lines (`source/internal-overlays/shop/catalog_java.txt`), but production must discover/generate from the actual registry so newer/older Armory versions do not silently lose content.

Include all legitimate registered items in namespace `cobblemonarmory` unless explicitly blacklisted as technical/non-item content.

Examples that must not disappear:

- trainer hats/accessories;
- Squirtle glasses;
- Slowking crown;
- Greninja scarf;
- Galarian Weezing hat;
- Eevee/Eeveelution sets;
- Charizard, Blaziken, Corviknight, Torterra, Haxorus, Mimikyu, Metagross, Rayquaza, etc.;
- shiny armor variants;
- Zacian/Ceruledge/Starmie/Sirfetch'd weapon items;
- Tinkaton/Metagross hammers;
- Bastiodon shield;
- Primeape gloves;
- crafting/material drops/upgrades.

## Price ceiling and progression fit

Quest reward scale: ordinary max around 20 BeastCoin.

Absolute default ceiling: **500 BeastCoin**.

Recommended baseline:

| Tier | Default price |
|---|---:|
| common material | 25 BC |
| rarer material | 40–75 BC |
| hat/accessory | 60–80 BC |
| normal armor piece | 90–130 BC |
| normal weapon/tool | 180–260 BC |
| normal shiny armor piece | 180–260 BC |
| Rayquaza armor piece | 250–320 BC |
| shiny Rayquaza piece | 360–450 BC |
| Zacian Sword | 500 BC |
| Bicycle | 500 BC |

Every price must be configurable/data-driven.

## PokéMall baseline

- 16 Poké Ball = 20 BC
- 8 Great Ball = 30 BC
- 8 Ultra Ball = 45 BC
- 8 Potion = 16 BC
- 6 Super Potion = 24 BC
- 4 Revive = 40 BC

## Networking

Use dedicated payloads, for example:

- S2C `ShopOpenPayload(shopId, balance, entries, categories, ...)`
- C2S `ShopBuyPayload(shopId, entryId, quantity)`
- S2C `ShopUpdatePayload(...)` or refreshed open payload

Do not trust a client-sent price or item registry id.

## Transaction acceptance cases

Must test:

- 0 balance;
- insufficient funds;
- exact balance;
- greater balance;
- inventory full;
- item missing from registry;
- BEconomy unavailable;
- rapid double-click;
- reopen/reconnect;
- two players buying concurrently.

No silent loss of currency and no dupes.
