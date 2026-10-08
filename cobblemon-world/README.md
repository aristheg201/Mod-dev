# CobblemonWorld

Minecraft 1.21.1 / Fabric / Java 21 / Cobblemon 1.8.1+1.21.1. Mod id: `cobblemonworld`.

CobblemonWorld owns its Trainer Phone, seven-Act campaign, dedicated NPC dialogue screen and conversation journal, branching phone conversations, stable objectives/navigation, services, faction and island-war state. Trainer teams scale to the player's party. NPCs remain at authored anchors while looking toward players and making restrained idle glances. Ren, Elle and Tomo use a dedicated merchant screen with server-authoritative BeastCoin purchases. Elle enumerates both Armory and Armors item registries.

Build with `./gradlew clean build` using Java 21. Install the regular production-remapped JAR from `build/libs`; source/dev JARs are not runtime mods. See [production build and QA instructions](docs/PRODUCTION_BUILD.md) for exact dependencies, optional-mod handling, shop overrides, trainer scaling and test fixture controls. See the delivered runtime report for exercised player paths and explicit UNPROVEN coverage.

Existing flags, contacts, messages, quests, placements and faction saves are retained. Story/objective migration selects valid gameplay targets; legacy compatibility IDs are not rendered as player-facing copy. Town 8's Harbour Marshal Liora and Captain Dorian qualify the player before Battle Tower, Royal League, School of Wolf and the story-spawned final encounter.

The expanded content has 70 main stages, 43 substantial regular side chains, a 24-stage Weather Duo season and 30 authored competitive rosters. Counts describe shipped data; actual runtime coverage is separately reported. Both Groudon and Kyogre are one-time postgame partnership rewards after the complete seasonal investigation and guardian challenges.

See [campaign flow](docs/narrative/CAMPAIGN_FLOW.md), [side stories](docs/narrative/SIDE_CHAINS.md), [Weather Duo](docs/narrative/WEATHER_DUO.md), [trainer teams](docs/narrative/TRAINER_TEAMS.md) and [save migration/map authoring](docs/narrative/SAVE_MIGRATION.md). This mod supplies gameplay and authored content, not a prebuilt adventure map. The map owner places NPCs and inspection points, and configures the final meeting location.
