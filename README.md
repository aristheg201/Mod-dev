# SVArcade — Card Worlds

Production-hardening branch for the internal Minecraft 1.21.1 Fabric TCG module originally built in `E:\\YU-GI-OH`.

This branch deliberately starts from the working Codex implementation rather than from generated mockup images. The source of truth is runnable code plus automated/runtime QA evidence.

## Target

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.18.4
- Fabric API 0.116.6+1.21.1
- Cobblemon 1.8.1
- Fabric Loom 1.7.4

## Build

```bash
gradle clean verifyCardWorlds
```

The production artifact is the remapped `build/libs/SVArcade-TCG-0.2.1-production-pass.jar`. The QA driver is built separately and must not be shipped to players.

The SVArcade host JAR is optional for compilation. If `libs/SVArcade-0.6.8.jar` is present it is added to the local runtime for integration QA; the standalone TCG module is not allowed to require that binary just to compile.

## Current hardening pass

- Removed the custom TTF path that produced corrupted glyphs in one visual-QA run; UI now uses Minecraft's stable text renderer.
- Removed the 2.7 MB baked arena image. Arena/world panels use deterministic procedural rendering so layout is resolution-independent and the repository does not hide presentation behind a large binary reference image.
- Fixed Cobblemon model clipping to use logical GUI scissor coordinates instead of pre-transforming them a second time. Model scale is clamped and missing models now produce an explicit diagnostic fallback instead of an unexplained blank card.
- Reworked pack presentation into an actual timeline: entry, shake, burst, sequential card flip and rarity pulse, with a skip/reveal-all path.
- Added CI and explicit acceptance gates. Mockups are references, never proof of implementation.

## Gameplay baseline retained from Codex

The branch keeps the implemented duel engine, response/chain flow, collection/deck ownership, SQLite persistence, marketplace/trade escrow, pack pity/idempotency, world/NPC rewards, ranked validation and the real-client visual QA driver.

The supplied Codex evidence from 2026-09-29 recorded 31 passing tests (13 engine + 18 economy). This branch does **not** reuse that number as proof after code changes: GitHub CI must run again and become the branch authority.

See `docs/QA-GATES.md` and `docs/RULES-REFERENCE.md`.

<!-- CardWorlds local source export branch -->
