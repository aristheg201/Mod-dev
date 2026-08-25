# Fabric MMO ports overlay M1.5

Clean-room parity batch derived from the supplied MMOItems 6.10.1 and MythicLib 1.7.1 bytecode surfaces.

This batch locks three runtime contracts before wiring them into the Fabric-facing player session:

- inventory/equipment diff and lifecycle ordering;
- exact per-equipped-item modifier lifetime by UUID/object cache;
- crafting queue absolute-time persistence and removal compaction.

Important original semantics preserved here:

- unchanged equipped items are detected by the platform ItemStack hash and produce no update;
- replacement order is old unequip/unapply before new equip/apply;
- equip/unequip events occur before modifier apply/unapply;
- item modifier caches unregister the exact modifier instances they registered, not every modifier sharing a stat key;
- queue records use `Recipe`, `Start`, `Completion` and preserve absolute timestamps across reloads;
- queue item UUIDs are runtime identity only and are regenerated on load;
- removing a craft advances later completions by `min(removed.left, removed.recipe.craftingTime)`;
- `QueueItem#getElapsed()` keeps the original `max(craftingTime, now-start)` behavior.

Run locally with Java 21:

```bash
./build-offline.sh
```

Expected gate:

```text
MMOITEMS_INVENTORY_CRAFTING_MODIFIER_RUNTIME=PASS
```
