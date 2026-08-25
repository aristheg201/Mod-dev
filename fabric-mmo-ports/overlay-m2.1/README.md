# Overlay M2.1 - MythicMobs Fabric execution runtime

This overlay replaces the previous thin MythicMobs Fabric facade with a real server-side legacy skill execution path.

Included source:
- mapping-independent legacy skill parser/runtime
- Fabric `SkillPlatform` adapter
- configured skill casting and nested metaskills
- mob skill trigger binding (`onSpawn`, `onDamaged`, `onDeath`, `onKill`, `onAttack`, `onInteract`, `onTimer`)
- delayed task scheduler
- entity/location targeters and conditions
- implemented mechanic families for damage/heal/status/movement/teleport/summon/explosion/lightning/variables/commands/items/blocks/entity state
- raw legacy mob sections retained at runtime rather than collapsed into a small facade model
- mob attributes/options and configured level modifiers applied on spawn

The pure Java engine smoke test currently registers 117 mechanic names/aliases, 48 condition names/aliases, and 24 targeter names/aliases. These counts are implementation coverage markers, not a claim of full MythicMobs parity.

Archive reconstruction:

```bash
cat source.tar.gz.b64.part* | base64 -d > source.tar.gz
echo '4796017166c914ae52ad7f717f24a2f6c2fdacc68738e7412a72d6a40d529328  source.tar.gz' | sha256sum -c -
tar -xzf source.tar.gz
```

The archive root is `fabric-platform/`, so it is intended to be extracted over the reconstructed workspace after the current module source copy step.
