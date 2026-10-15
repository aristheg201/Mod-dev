# SVEXTRACT implementation checkpoint — 2026-10-15

This is an unfinished implementation, not the requested production release. The current core is a unified client/server Fabric JAR with external Point Blank, SVFrame and BEconomy integrations. Only SQLite is nested. Protocol 4 and the same core are required on both sides. The internal version remains 0.1.0-alpha.1; this is not a production designation.

Current core SHA-256: `1186707b0e5e46cb2d8d66772377ca9c3403b20f7a26e395b62f1d0eb274ed18` (14,567,510 bytes). Native testing used Minecraft 1.21.1, Java 21 and Fabric Loader 0.19.5 with the full pinned external pack, including Cobblemon.

## Operational and verified within the stated scope

- Native Operations lobby, actual equipped-player preview, risk confirmation, mode availability and Worlds/Hubs navigation. Real squad invitation/membership code exists; shared squad deployment is not implemented.
- Hub stash and loadout UI plus a physical Haven-9 stash terminal. Whole-stack moves preserve item components and identities, reject stale revisions/full destinations, and use a durable transfer intent followed by verified/fsynced player save and exactly-once ownership commit. Native deposit, dedicated-server restart, withdrawal and injected pending-intent recovery passed. Actual Point Blank components matched exactly across the round trip.
- Original editable Blockbench supply/drop/storage projects, bundled textures/models and static open-lid states. The Battleground model is registered; its gameplay is not implemented. Interactive Blockbench-editor use is not claimed.
- Physical extraction crates open a native loot preview and validated Take action. A full-pack native client took eight new Point Blank rounds, increased its ammunition from 8 to 16, evacuated with optional research incomplete, and persisted permanent item identities without temporary raid markers. Settlement closed once and instance tickets were released.
- Private Counter-Strike-style defusal, one human plus four friendly/five hostile bot seats. The latest native shortened two-round test bought armor for $650 from $800 match cash, planted at A, detonated, swapped sides, won 2–0 and restored the original inventory in Skyrim. The interval recorded seven real player firearm requests and 90 bot shots; cumulative server bot shots were 103 including the preceding PvE mission. Native human defusing, default-length matches, public matchmaking and economy payouts remain unverified or unfinished. Normal reload input was exercised, but an independent magazine/reserve assertion was not captured before settlement.
- Author Skyrim terrain is applied to the isolated modular server: 164 regions, spawn (4834,97,4574). A native client entered through Worlds/Hubs and rendered the real village and Mission Control. This is protected hub terrain, not a completed Skyrim extraction campaign.
- Original Redline railway and Blackglass industrial layouts remain isolated raid maps. The pinned 47×47×47 legacy Vault-public room ships inside the core and renders under Blackglass; this is not all modern Vault Hunters maps or monsters.
- Existing NPC damage/kill-command guards remain separate from combat bots. Generic, magic, explosion, void and kill-command checks have native evidence; full firearm/skill-specific coverage remains required.

## Recovery and test limits

Inventory save verification compares entries by unique slot while retaining exact item counts and components. Vanilla serializes main slots before armor/offhand, so list insertion order must not keep valid stash withdrawals or loot settlements pending. Duplicate slots remain rejected. The latest full-pack native regression withdrew the identified Glock into slot 0 while ammunition occupied slots 5/6 and armor occupied slot 102. Intent order [5,6,102,0] matched vanilla save order [0,5,6,102]; components and armor matched, stash revision advanced 3 → 4, and the transfer closed with no pending operation. See `qa/reports/stash-slot-order-native-20261015.json`.

A native reconnect test exposed an ordering bug: recovery could close before client handshake verification and leave deployment locked. The fix publishes recovery readiness independently of handshake timing. A deterministic 40-tick delayed-handshake test then recovered its durable intent, deployed, acquired new loot and extracted successfully.

An earlier run lost its server, client and display processes together during evacuation, without a server crash report or OOM kill. Its incomplete result is preserved. A QA error that counted existing ammunition as new loot was corrected; the successful rerun explicitly required 8 → 16 ammunition. Injected intent tests model durable boundaries; they are not power-cut tests.

Sixty-two JVM regressions and six offline terrain-installer tests pass. Real author regions were validated and installed into a separate offline fixture while preserving survival metadata and a player-data marker. The optional QA mod is excluded from the deliverable core.

## Measured performance

The latest private defusal diagnostic measured 19.642 TPS, MSPT p95 50.289 ms and p99 184.946 ms. It fails the requested targets. A prior one-client hub sample measured 20 TPS, p95 15.657 ms and p99 69.935 ms; it is not an empty-server Gate A test. The workloads differ, so these are not optimization comparisons.

Host: Intel Xeon Platinum 8573C, four-CPU quota, 16 GiB memory limit; server and software-rendered client shared that host with 3 GiB heaps and two JVM-visible processors each. The full-pack startup thread sample showed time in Cobblemon GraalJS/Showdown move initialization. No 20-player, long-duration stability or production-readiness claim is made. Counters are cumulative; tick percentiles cover a bounded sample ring.

## Unfinished requirements

Public 5v5 and two-minute autofill; real shared squads/PvPvE; 50–60-player Battleground and supply-drop gameplay; Black Zones; Apex; downed/revive systems; full Dead Signal story/boss acceptance; functional permanent-economy merchants/payouts; crafting, research and expanded hideouts; insurance/trading; full class/skill and firearm compatibility; maximum-health reconnect/healing-camera acceptance; Pokémon-gun runtime acceptance; further maps/boss assets; cinematic effects; complete UI polish; malicious-client/container coverage; all multiplayer/performance/stability gates.

Skyrim terrain delivery is separate server save data. Install all terrain region parts through the stopped-save installer; clients need only the core and their normal external mods. The original author URL and import provenance remain documented. Azeroth archive bytes are unavailable; Cyberpunk could not transfer through the attachment tool's size limit; modern Vault/Mowzie monsters are not included.

Per-requirement status is in `qa/acceptance/confirmation.csv`. Unsupported engines remain disabled. Production designation remains blocked.
