# SVHub Native Platform 0.2.0

SVHub 0.2.0 owns its player-facing platform runtime. ShadowCrates, SkiesSkins, SVArcade, and other gameplay mods are not required by the implementation.

## Server authoritative boundaries

- The client submits only typed module/action intents.
- Gacha winner, pity, wallet mutation, cosmetic ownership, game state, cards, dice, shops, combat, rewards and matchmaking are server-owned.
- Skin IDs/aspects/prices are loaded from the bundled immutable catalog. The client cannot submit an arbitrary command, price or aspect.
- Per-player profiles are saved independently on a single low-priority I/O worker. Rapid mutations coalesce to the newest snapshot.
- One active Arcade session per player; session count is capped and completed sessions expire.
- Network intent rate limiting and packet string caps are enforced before dispatch.

## Built-in modules

- Native wallet: HunterCoin, BeastCoin, Arcade Token, Gacha Ticket.
- Native CS:GO-style gacha: weighted rarity, pity and duplicate refund.
- Native skin ownership/shop/equip for the 658 bundled skin definitions.
- Vanilla companions.
- Pokémon Chess: castling, en passant, promotion, check/checkmate/stalemate, repetition, 50-move, insufficient material, clocks and bot.
- Xiangqi: palace, elephant river, horse leg, cannon screens, flying generals, clocks and bot.
- Cờ Cá Ngựa: server dice, home/deploy/capture/safe squares/exact finish, bots.
- UNO: 108-card deck, skip/reverse/draw/wild rules and bots.
- PokéDraft card duel.
- Pokémon TFT: shop, bench, deploy, combine, traits, economy, auto-combat and bot.
- Pokémon Tower Defense: path, waves, bosses, tower deploy/upgrade/sell and targeting.

## Client

The JAR ships native pixel UI and module/game icons under `assets/svhub/textures/gui/native/`. Skin model/texture/aspect assets remain in the server resource pack because they are Cobblemon resource-pack content rather than executable gameplay logic.
