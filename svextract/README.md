> **2026-10-15 source update:** new standalone rule engines, weighted raid loot and GitHub Gradle build routing are described in [docs/SOURCE_UPDATE_2026-10-15.md](docs/SOURCE_UPDATE_2026-10-15.md). This is not a certified production build.

# SVEXTRACT: Fracture Protocol

Minecraft 1.21.1, Fabric, Java 21. This is an unfinished corrective implementation, **not a production-ready release**. Work is on `feature/svextract-foundation`; no active production installation or player data is changed.

## Current architecture

Build one SVEXTRACT client/server JAR. Install Fabric API, Point Blank, the SVFrame modules and BEconomy separately with their compatible dependencies. Cobblemon is optional. The retired all-mods bundle is no longer supported: `-PpersonalBundle` fails with an actionable message. The only nested library is SQLite JDBC. Original SVEXTRACT textures, models, sounds and localization remain in the core JAR.

The build uses Loader 0.19.5; SVEXTRACT declares >=0.18.4. Other installed mods can require a higher minimum (the pinned Kotlin release requires 0.19.5). Match client/server SVEXTRACT versions and protocol; do not mix old bundles with the new core.

```sh
./gradlew build
```

Output: `build/libs/svextract-0.1.0-alpha.1.jar`. The existing version string is not a production designation. Run `./gradlew productionCheck` to inspect the evidence gate; production acceptance remains incomplete.

## Corrective implementation

- Native Operations lobby with the equipped player, six mode entries, bot/difficulty selection, loadout access, squad invitations, balance reads and explicit equipment-risk confirmation.
- Training and private Operations use the extraction journal. Operations uses Blackglass Industrial District, optional research, three physical supply caches and two evacuation points. Death, abandonment and timeout lose deployed equipment. Training preserves the original loadout.
- Private Versus uses one human, four allied gun bots and five opposing gun bots in Counter-Strike-style defusal rounds, with a native buy menu, match-local cash, planting/defusing, halftime and one life per round. Real 5v5 matchmaking and economy rewards remain unfinished.
- Native stash/loadout and physical stash terminal: revision-checked whole-stack transfers, durable intent, verified player save and recovery. Native supply-crate loot UI validates every claim on the server.
- Editable original Blockbench crate projects and bundled block/item models/textures. Battleground supply-drop gameplay remains unfinished.
- Native Worlds/Hubs navigation distinguishes installed/prepared Haven-9 and author Skyrim worlds.
- Service NPC roles are protected separately from combat bots, including damage sources that bypass vanilla invulnerability.
- Async chunk preparation, bounded block changes, instance admission, per-tick bot firearm work, dirty HUD updates, durable checkpoints and a single bounded SQLite writer are part of the core.

Implementation is not runtime verification. See [current rebuild record](docs/REBUILD_TRACKER.md) and [acceptance table](qa/acceptance/confirmation.csv). Disabled modes cannot be enabled merely by editing their menu flags.

## Maps

Redline is the original railway mission. Blackglass is original industrial geometry with streets, electrical works, research warehouse, foundry, freight lanes, elevated cover, an underpass and two exits. It ships the pinned **legacy Vault-public big1 room**, not all current Vault Hunters maps or monsters. The server exports its bundled NBT to `config/svextract/templates/vault_legacy_haven.nbt` on first start; [map provenance and setup](docs/MAPS.md) records the exact source/hash. Author map data is server-side; no third-party mod or external client resource pack is embedded for it.

Skyrim author terrain is installed in the current isolated QA server and is exposed through Worlds/Hubs; a full-pack native client has entered and rendered the author village and Mission Control. Crafting Azeroth remains blocked on unavailable map bytes. Neither is represented as Blackglass or asserted playable from a menu name.

## Local QA

With the pinned external development dependencies prepared:

```sh
./gradlew build remapQaJar exportQaClasspath -PcompatQa -PqaScript
python3 tools/run_native_qa.py --java /path/to/java21 --display :100 --mode versus
```

The optional QA mod drives a real local native client and is excluded from deliverables. It connects only to `127.0.0.1:25565` with an identified offline test username. Do not use this setup on a public server. See [admin recovery](docs/ADMIN.md), [integration revisions](docs/INTEGRATIONS.md) and [historical QA](qa/README.md).

Unfinished requirements include expanded hideouts/services, idempotent economy payouts, real multiplayer queues, rival extraction squads, Battle Royale, Apex, insurance, full progression, further maps and multiplayer/performance acceptance. Compilation alone does not close these requirements.


## In-development environmental density pass (2026-10-15)

See [docs/CONTENT_DENSITY_2026-10-15.md](docs/CONTENT_DENSITY_2026-10-15.md) for the new map decoration, POI detection, explicit loot-config migration, bounded patrol routing, seven optional Operations contracts, test evidence, and build limitations. The original compiled checkpoint JAR **does not contain these source changes**; it must be rebuilt and validated before use.
