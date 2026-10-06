# Cobblemon World

Target: Minecraft 1.21.1, Fabric, Java 21, Cobblemon 1.8.1, client + dedicated server.

This branch is the first-party Cobblemon World RPG implementation. It replaces reliance on the Smartphone mod for campaign UX while keeping all runtime assets inside this mod JAR.

Implemented foundation:
- persistent per-player campaign record
- persistent no-reset level cap
- over-cap send-out, battle, capture, natural-spawn and XP enforcement
- Trainer Phone item
- story/contact/message/side-quest registry
- ??? first-contact script
- faction 2.8.0 soft bridge
- Saturday island-war scheduler/data
- NPC placement model
- explicit TOBA two-phase encounter state machine
- English/Vietnamese localization
- branch CI build

Locked gameplay rule: over-cap owned Pokemon keep their real level. They are simply unusable until the player's cap catches up. Wild Pokemon above the spawning player's cap are cancelled rather than down-leveled.

See docs/FULL_SCOPE.md for the complete production contract.
