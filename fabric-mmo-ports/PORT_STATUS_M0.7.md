# Fabric MMO Ports M0.7 status

Target remains Minecraft 1.21.1, Java 21, Fabric Loader >= 0.18.4. GitHub Actions remain intentionally unused.

M0.7 continues the clean-room parity port and is still not declared final/full parity.

## Verified gates

- Java 21 internal build: PASS
- `MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS`
- `MMOCORE_SOCIAL_WAYPOINT_LOOT_RUNTIME=PASS`
- `MMOITEMS_INTERACTION_REFORGE_STATE_RUNTIME=PASS`
- `MYTHICMOBS_RANDOMSPAWN_THREAT_FACTION_RUNTIME=PASS`
- YAML corpus: `1166 / 1166`
- MMOCore legacy classes mapped: `71`
- MMOItems templates mapped: `2085`
- MythicLib skill definitions mapped: `1043`
- Mythic skill lines parsed: `39260`
- `FULL_LEGACY_CONFIG_MAP=PASS`

## Runtime additions after M0.6

### MMOCore
- profession registry/state/leveling runtime
- EXP-source gating for professions
- quest lifecycle: available/start/progress/complete/cooldown/cancel
- quest parent, player-level and profession-level requirements
- class selection runtime backed by legacy class definitions
- class attribute evaluation by level

### MMOItems
- crafting recipes and ingredient consumption
- success-roll crafting output
- upgrade max-level/success/destroy/stat-scale rule model
- item action restrictions for drop/death-drop/repair/craft/smelt/smith/enchant

### MythicLib
- trigger runtime with priority-ordered handlers
- arithmetic expression runtime with placeholder variables
- existing skill/cooldown/scaling/stat/damage/event runtimes retained

### MythicMobs
- platform abstraction for damage/heal/message/sound/particle/teleport/velocity/potion/summon/variables/tags/stance/delay
- initial built-in mechanics installed against that abstraction
- mechanic pipeline smoke-tested through a platform damage callback

## Important remaining gates before final release

The four runtime JARs produced by this milestone are still internal reconstructed-runtime artifacts, not final Fabric-remapped release JARs.

Still required:
- Fabric/Yarn 1.21.1 entrypoints, lifecycle hooks, mixins and platform adapters
- Loom remapJar and dedicated Fabric server boot
- full command surfaces and vanilla ScreenHandler GUIs
- exact public API/event compatibility where behavior is relied upon
- complete MMOItems stat/listener/crafting/modifier/upgrading catalogue
- complete MythicLib script/trigger/placeholder/data/profile/sql/RPG surfaces
- complete MythicMobs mechanic/condition/targeter/mob/AI/drop/item/aura/timer/spawner/API catalogues
- MMOCore remaining economy/social/friend/loot-chest/GUI/command details

No percentage is claimed from class counts. Full parity is only declared after behavior/API and Fabric boot gates are proven.
