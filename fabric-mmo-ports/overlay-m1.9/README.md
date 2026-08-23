# Fabric MMO ports overlay M1.9

MythicLib passive skill / trigger routing parity slice reconstructed against MythicLib 1.7.1 bytecode.

Local Java 21 gate:

```text
MYTHICLIB_PASSIVE_TRIGGER_RUNTIME=PASS
```

Locked semantics in this batch:

- passive skill construction rejects active trigger types;
- `TIMER` period is exactly `max(1, (long) timer) * 50ms`;
- passive registration/removal is UUID keyed;
- replacement/removal closes the exact previous modifier instance;
- `LOGIN` fires on session open;
- timer ticking requires an open session and skips spectator state;
- timer `lastCast` is keyed by handler ID and is updated before casting;
- action-hand-specific trigger routing delegates to the original `EquipmentSlot.isCompatible(ModifierSource, modifierSlot)` rules;
- `MELEE_WEAPON` and `RANGED_WEAPON` require modifier slot equality with the action hand;
- fixed-source compatibility such as `OFFHAND_ITEM` preserves the original source-specific rule rather than being incorrectly filtered as a generic hand item;
- built-in trigger flags preserve silent/passive/action-hand-specific metadata, while custom trigger IDs remain exact/case-sensitive.

Source archive SHA-256 after base64 decode:

`523338a2a81bc97c19bddcba8a18ac3a59fe3eaa535336e2641cbe1e063eca9`

This overlay remains outside the current GitHub Actions path filter.
