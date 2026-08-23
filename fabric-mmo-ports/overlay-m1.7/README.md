# Fabric MMO ports overlay M1.7

MMOCore saved-class, binding and experience parity slice reconstructed against MMOCore 1.13.1 bytecode.

Local Java 21 gate:

```text
MMOCORE_SAVED_CLASS_BINDING_EXP_RUNTIME=PASS
```

Locked semantics in this batch:

- `SavedClassInformation` snapshots class level, EXP, skill/attribute points, reallocation points, resources, skill levels, attribute levels, skill-tree points, node levels/times-claimed, bindings and unlocks.
- class switching saves the old class slot subject to `save-default-class-info`, clears old advancement/bind state, applies sharing rules, installs the destination class, then restores destination advancement/resources.
- shared skill points preserve unspent + spent total, using `sum(skillLevel - 1)` exactly like the original.
- shared attribute points preserve unspent + spent total, using summed attribute levels.
- destination class slot is unloaded after it is applied.
- saved bindings resolve against the destination class; removed/renamed skills are skipped instead of creating invalid runtime bindings.
- class assignment closes old bindings, clears them, reapplies temporary triggers, then installs hard binds.
- binding rejects slot `<= 0` and permanent skills, and unbinds the previous slot value first.
- `CHOOSE_PROFILE` suppresses the level-change event.
- positive EXP applies additional-EXP and booster multipliers before the mutable/cancellable event, then loops across multiple level-ups and zeroes EXP at max level.
- non-positive EXP bypasses positive-gain event processing and clamps final EXP to zero.
- class change has a cancellable Fabric-side event surface.

The source archive is stored as `source.tar.gz.b64`.

SHA-256 after base64 decode: `3604b61ab7c73bcf76d4dc3e67eee70f71758efe0a9d9c87799c24123fbf7fea`.

This path is intentionally not present in the GitHub Actions path filter yet, so committing M1.7 does not start Actions.
