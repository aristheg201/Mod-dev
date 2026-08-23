# Fabric MMO ports local build M2.1

This snapshot was rebuilt locally from the current `feature/fabric-mmo-ports` source tree with Java 21 after the M1.5-M2.0 parity slices were integrated.

## Local build gates

- `MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS`
- `MYTHICLIB_PROFILE_COOLDOWN_SESSION_RUNTIME=PASS`
- `MYTHICLIB_PASSIVE_TRIGGER_RUNTIME=PASS`
- `MMOCORE_SOCIAL_WAYPOINT_LOOT_RUNTIME=PASS`
- `MMOCORE_CLASS_SKILL_PLAYERDATA_EVENT_RUNTIME=PASS`
- `MMOCORE_SAVED_CLASS_BINDING_EXP_RUNTIME=PASS`
- `MMOITEMS_INTERACTION_REFORGE_STATE_RUNTIME=PASS`
- `MMOITEMS_INVENTORY_CRAFTING_MODIFIER_RUNTIME=PASS`
- `MYTHICMOBS_RANDOMSPAWN_THREAT_FACTION_RUNTIME=PASS`
- `MYTHICMOBS_SKILL_COMPONENT_REGISTRY_RUNTIME=PASS`
- all four Fabric `ModInitializer` entrypoints initialize in standalone validation.

## Real config corpus gate

The local build was validated against the supplied live config archive:

- MythicLib: 1009 skills
- MMOCore: 70 classes
- MMOItems: 3230 items, 798 abilities, 6640 recipes
- MythicMobs: 867 mobs, 24734 skill lines, 0 parse failures

## JAR SHA-256

- MMOCore: `395e619ef74fee453ef9a6107ab4c88a7f416b573398e2e3c2fd523bf4afbd10`
- MMOItems: `fc425c383dd5bcf28828cf952a0cf6e5b53d60e16e543c18eddaef8017d9f22c`
- MythicLib: `e9de9546f65875647b00a2408d6243eee9c03a763b5b363da307509e604c7acd`
- MythicMobs: `a0061f3c6bc8ff069e38bb70850f7c9955a69ed47dc09bd3a049b47856c2317b`

## Scope truth

This is a real locally compiled integration snapshot, not an empty placeholder JAR. It is also **not being labeled as 100% original-plugin parity yet**. `ORIGINAL_SURFACE_AUDIT.md` still lists original systems that have not all been ported/wired. That distinction stays explicit so a dev build does not get mislabeled as production-complete.
