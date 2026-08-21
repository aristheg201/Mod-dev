# Reconstructed parity runtime slice

The M0.5 source ZIP was not present in this branch, so this commit preserves the clean-room runtime work completed from the supplied original JAR surfaces. It does **not** claim full parity or final Fabric-remapped JAR status.

Reconstruct locally:

```bash
cat source.tar.gz.b64.part00 source.tar.gz.b64.part01 source.tar.gz.b64.part02 source.tar.gz.b64.part03 | base64 -d > source.tar.gz
tar -xzf source.tar.gz
cd fabric-mmo-rebuild
./build-offline.sh
```

Source archive SHA-256: `bf34d2d8a6c8711bf5fa8769f02443c1b8fede9167cb0ec770e207098c79cb89`

Java 21 smoke gates in this slice:

- `MYTHICLIB_RECONSTRUCTED_RUNTIME=PASS`
- `MMOCORE_SOCIAL_WAYPOINT_LOOT_RUNTIME=PASS`
- `MMOITEMS_INTERACTION_REFORGE_STATE_RUNTIME=PASS`
- `MYTHICMOBS_RANDOMSPAWN_THREAT_FACTION_RUNTIME=PASS`

Implemented here:

- MythicLib: stat modifier aggregation, expiring modifiers, typed damage packets, ordered combat pipeline, event bus.
- MMOCore: 120s request semantics, party owner transfer/unregister, guild runtime + atomic persistence, waypoint routing/Dijkstra/dynamic entries, weighted loot with level bias.
- MMOItems: colored and Uncolored sockets, durability state, configurable reforge carry-over, requirement/interaction pipeline.
- MythicMobs: explicit spawn reason model/mapping, prioritized RandomSpawn ADD/REPLACE/DENY, threat table, faction relations.

Original surface audit used as reference:

- MMOCore 1.13.1: 661 first-party classes.
- MMOItems 6.10.1: 683 first-party classes.
- MythicLib 1.7.1: 1471 classes below `io.lumine.mythic.lib`.
- MythicMobs 5.6.2: 9850 classes below `io.lumine.mythic`, including substantial shaded libraries, so class count is not used as a parity percentage.

Remaining work is tracked as behavior/API gates, not fake percentage completion. Exact Fabric/Yarn spawn reason hooks, commands/ScreenHandler GUIs, remaining MMOItems stat/interaction/reforge options, MythicMobs mechanics/conditions/targeters/API, and the broader MythicLib surface are not declared complete by this slice.
