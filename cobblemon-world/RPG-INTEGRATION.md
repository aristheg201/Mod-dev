# Cobblemon World - RPG integration

## Server stack

Cobblemon World soft-integrates with:

- SVFrameLib 1.7.1+
- SVFrameMMO 1.13.1+
- SVFrameMMO: Cobblemon Integration 0.1.16+

These remain suggested integrations, not hard dependencies. Cobblemon World boots without them.

## TOBA phase II bridge

When SVFrame is installed:

- DASH, GUARD, BREAK and ANCHOR consume SVFrameMMO Stamina.
- PURGE and PARTNER consume SVFrameMMO Mana.
- Skill cooldowns use effective COOLDOWN_REDUCTION, capped at 65% for this encounter.
- Player TOBA skill damage is emitted through SVFrameLib PlayerMetadata.attack(...), preserving RPG damage processing, damage types, stat modifiers and passives.
- BREAK uses SKILL + PHYSICAL.
- PURGE uses SKILL + MAGIC.
- PARTNER uses SKILL + MINION.
- TOBA attacks use normal Minecraft damage entrypoints so SVFrameLib can reconstruct/process incoming hits.
- SVFrameMMO combat state is marked at encounter start and during RPG actions.
- Trainer Phone shows live SVFrame class, level, Stamina and Mana.

If SVFrame is absent or an API signature cannot be resolved, the bridge logs one warning and falls back without preventing the mod from booting.

## Runtime diagnostic

Use /cworld story rpg status

The command prints class, RPG level, STR/DEX/INT, Stamina, Mana, Cooldown Reduction and whether the SVFrameLib damage bridge is active.

## Skill tuning

TOBA skill costs, cooldowns and base damage are data-driven in data/cobblemonworld/boss/toba_skills.json.

No SVFrame stat or RPG resource value is copied into Cobblemon World's own persistence.