# Production pass build and test instructions

Build from this module using Java 21 and the included Gradle 8.12 wrapper:

```sh
./gradlew clean build
python3 tools/check_production_data.py
```

The production file is `build/libs/CobblemonWorld-<version>.jar`. The `-sources.jar` and `-dev.jar` are not runtime mods. Loom remaps the regular JAR to Fabric intermediary names. The complete source handoff includes the supplied `libs/BEconomy-1.5.jar` solely as a compile dependency; it is not bundled inside CobblemonWorld.

Runtime baseline: Minecraft 1.21.1, Java 21, Fabric Loader 0.18.4, Fabric API 0.116.17+1.21.1, Fabric Language Kotlin 1.13.6+kotlin.2.2.20, Cobblemon 1.8.1+1.21.1. Ordinary shops require the supplied BEconomy 1.5 API (`org.krripe.beconomy`). That mod is server-only and also needs Placeholder API. Explicitly configure a currency type named BeastCoin. HunterCoin is independent. If BeastCoin or BEconomy is absent, purchasing is disabled.

Armory 1.5.4 requires GeckoLib at runtime. Elle enumerates the runtime item registries of both `cobblemonarmory` and `cobblemonarmors`. MapKit is optional; Tomo's bicycle is visibly unavailable when `mapkit:bicycle` does not exist.

Shop definitions live in `data/cobblemonworld/shops`. Datapacks can replace them; a complete server-owner override can also be placed at `config/cobblemonworld/shops/<shop-id>.json`. Reload invalidates open shop sessions; interact again to reopen. Each price is 1–500 BeastCoin. Quantity is a bundle count (1–16), with bundle contents and price resolved by the server. Fashion rules and overrides define categories and prices independently of rendering.

The normal player reset commands are `/cworld story objective reset` and `/cworldresetobjective`. Placement commands are admin-only. Use `/cworld npc place <id>` and `/cworld npc rotate <id>` to author NPC anchors; pitch is always normalized. Never manually place `mysterious`; configure the final encounter location in `config/cobblemonworld/server.json` instead.

The optional production QA drivers run only with explicit `cworld.qa.*` JVM properties. `runProductionCWorldServer` and `runProductionCWorldClient` launch the remapped JAR in separate processes. Run `python3 tools/prepare_runtime_qa.py` to verify/download the exact test dependencies and prepare an isolated localhost server with `eula=true`, offline authentication, port 25571, survival mode, and no operators. The client uses normal interaction and gameplay packets, captures the real framebuffer, and the server checks balances, inventory, party health, objective identity, and real battle victory flags. The isolated fixture runs in peaceful survival mode with no operators, supplies test Pokémon through server setup, and uses actual battle action/victory packets. Early scaling tests use party aces at levels 12 and 23; campaign transport tests use level 100. This does not assess campaign battle difficulty.

Use `-Dcworld.qa.restart=true` for the second dedicated-server/client run to verify saved state after both processes exit. Never use this fixture on an existing player world. See the delivered runtime report for actual coverage and unproven paths.

Trainer teams scale on the server at battle start. With `scaleTrainerLevels=true` (default), the trainer ace matches the strongest current party Pokémon and other members retain their authored level gaps, clamped to 1–100. `trainerLevelOffset` defaults to zero and permits a global owner adjustment in `server.json`. Team species, moves and gates remain authored. An NPC already fighting cannot have its party replaced by a second challenger.

The official Armors 1.6.0+1.8.1 download has a dependency typo (`1.8.1+1.12.1`). For the isolated test only, `config/fabric_loader_dependencies.json` declares the corrected Cobblemon dependency; no third-party classes or item definitions are patched:

```json
{"version":1,"overrides":{"cobblemonarmors":{"+depends":{"cobblemon":"1.8.1+1.21.1"}}}}
```

Build once before running server and client; use `-x remapJar` on their tasks to avoid concurrent remap writes. `-Dcworld.qa.services=true` selects a fresh `qa-runtime/server-services` world for phone, shop, navigation, localization and low-level scaling tests. Combine services with `-Dcworld.qa.restart=true` on both processes to test that world after a real shutdown. `-Dcworld.qa.resume=true` continues the campaign fixture from actual previously earned battle flags. `-Dcworld.qa.noeconomy=true` runs an isolated server with BEconomy omitted and proves purchase rejection. These modes are test-only and must never be enabled on a player server.

Screenshots use Minecraft's screenshot helper. The resource-pack QA mode temporarily selects an actual local test pack overriding the Poké Ball item model with Minecraft's diamond texture, then removes it; the item remains a Poké Ball. This checks resource resolution through the normal ItemStack renderer. It creates no synthetic screenshots.

The campaign QA party uses legal Life Orb items, 31 IVs, and a legal 252 Special Attack / 252 Speed / 4 HP EV spread after the scaled endgame defeated the earlier untrained party. Battle damage, enemy AI, and victory events are unchanged. This fixture measures transport and progression, not balance or normal item acquisition.
