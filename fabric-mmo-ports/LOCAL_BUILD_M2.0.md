# Fabric MMO ports local composite build M2.0

Built locally with Java 21 from the last Loom-remapped/dedicated-server-booted M1.4 baseline plus the post-M1.4 parity slices M1.5-M2.0.

## Local gates

- `MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS`
- `MYTHICLIB_PROFILE_COOLDOWN_SESSION_RUNTIME=PASS`
- `MYTHICLIB_PASSIVE_TRIGGER_RUNTIME=PASS`
- `MMOITEMS_INTERACTION_REFORGE_STATE_RUNTIME=PASS`
- `MMOITEMS_INVENTORY_CRAFTING_MODIFIER_RUNTIME=PASS`
- `MMOCORE_SOCIAL_WAYPOINT_LOOT_RUNTIME=PASS`
- `MMOCORE_CLASS_SKILL_PLAYERDATA_EVENT_RUNTIME=PASS`
- `MMOCORE_SAVED_CLASS_BINDING_EXP_RUNTIME=PASS`
- `MYTHICMOBS_RANDOMSPAWN_THREAT_FACTION_RUNTIME=PASS`
- `MYTHICMOBS_SKILL_COMPONENT_REGISTRY_RUNTIME=PASS`
- MythicMobs registry: 642 mechanic keys, 294 condition keys, 154 targeter keys.

The M1.4 baseline had already passed Fabric Loom `remapJar` and the dedicated Fabric 1.21.1 server boot gate. Post-M1.4 classes were compiled with `javac --release 21`, merged into those remapped JARs, then all legacy and new pure-runtime gates were executed directly from the merged JARs.

## Local binary SHA-256

- `MMOCore-Fabric-1.13.1-port-dev.jar` `5f97c8d20c0451bf6aa7a63dcfd06a91b37e0ae4c4113ef1b73b5eb742946c91`
- `MMOItems-Fabric-6.10.1-port-dev.jar` `912fde4c550061476965f5a6b07d20a900aaeef0d6bb97d99b03654ea74cf033`
- `MythicLib-Fabric-1.7.1-port-dev.jar` `3a6aacb9aea79c0c486e13705c660cfd2cf4ec989b6396563decd2c92b85672a`
- `MythicMobs-Fabric-5.6.2-port-dev.jar` `fff6a3a52a4becf9e1757ca8010ae8774aca3a25e5b321473ece11bf12777881`
- combined local zip `9b3ca3b907b28fcd268d075820222211babeebfeab01c510f6bdda0b798b5d16`

## Scope

This composite build proves archive integrity, Java 21 compatibility, retained M1.4 runtime gates, and the M1.5-M2.0 parity contracts present in the binary. It is not yet a claim of complete original-plugin parity: post-M1.4 contract runtimes still need full Fabric lifecycle wiring, and MythicMobs does not yet execute every original mechanic on Fabric.
