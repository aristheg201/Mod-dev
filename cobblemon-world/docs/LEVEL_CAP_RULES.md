# Level-cap rules — locked specification

The cap is progression state, not a level-reset mechanism.

## Ownership

- Existing Pokémon are never de-leveled when a player's cap changes.
- Traded, gifted, admin-granted, migrated or otherwise acquired over-cap Pokémon keep their real level.
- An over-cap owned Pokémon is locked from use until the player's cap catches up.

## Usage

- Sending an over-cap party Pokémon into the overworld is blocked.
- Starting any Cobblemon battle while an over-cap Pokémon is in the party is blocked.
- This applies to wild, NPC/trainer and PvP battles so the rule cannot be bypassed by battle type.

## Capture

- A thrown Poké Ball cannot capture a Pokémon whose level is greater than the thrower's current cap.

## Wild spawning

- Player-caused natural wild spawns are validated against the spawning player's cap.
- If the rolled Pokémon level is greater than the cap, the spawn is cancelled.
- The Pokémon is NOT silently scaled down.
- Scripted/command/NPC story spawns are not touched by this hook, so authored encounters remain controllable by story data.

## Experience

- Owned Pokémon at the cap cannot gain XP past it.
- A large XP award is truncated to one XP below the threshold for cap + 1.
- This prevents accidentally turning a legal party Pokémon into an unusable over-cap Pokémon.
- If a Pokémon is already over-cap, it remains at its real level and simply gains no more XP while locked.

## Persistence

- Per-player cap is stored in <world>/cobblemonworld/progression.json.
- Default cap is configured in config/cobblemonworld/server.json.
