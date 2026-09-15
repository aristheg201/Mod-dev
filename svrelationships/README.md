# SVRelationships

Production-targeted, server-side-focused Cobblemon relationship/life-simulation framework.

## Runtime baseline

- Minecraft 1.21.1
- Java 21
- Fabric Loader >= 0.18.4
- Cobblemon >= 1.8.0

## Architecture rules

- Java defines generic capabilities and engines.
- Server config defines gameplay; datapacks are not the gameplay authority.
- No species/rank/romance/reward/household gameplay rules are hardcoded.
- No user-facing literal text in Java or gameplay definitions; presentation resolves through localization keys.
- Command arguments use contextual suggestions from runtime registries/state.
- Performance work must preserve configured gameplay semantics.

## Modules

- `core`: platform-neutral domain state, registries and integration contracts.
- `fabric`: Fabric runtime, config loading, commands, persistence, GUI and optional integrations.

## Integration targets

Required: Cobblemon. Optional soft integrations: Text Placeholder API, LuckPerms, CobbleDollars, BEconomy, Common Economy API/other registered economy bridges.
