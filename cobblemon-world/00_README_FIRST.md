# CobblemonWorld source handoff

This is the full source module for Minecraft 1.21.1, Fabric Loader 0.18.4, Fabric API 0.116.17+1.21.1, Java 21 and Cobblemon 1.8.1+1.21.1. Mod ID: `cobblemonworld`.

Build: `./gradlew clean build`. Install the regular remapped JAR from `build/libs`; do not install source/dev JARs. The included BEconomy 1.5 JAR is a compile-only API dependency. BEconomy and optional item mods are separate runtime installations.

Start with `README.md` and `docs/PRODUCTION_BUILD.md`. Read `docs/narrative/CAMPAIGN_FLOW.md`, `MAIN_STAGES.md`, `SIDE_CHAINS.md`, `WEATHER_DUO.md`, `TRAINER_TEAMS.md` and `SAVE_MIGRATION.md` for shipped content and map authoring. Actual runtime status and limitations are in `docs/PRODUCTION_RUNTIME_QA.md`; counts and compilation are not runtime proof.

All changes belong to `feature/cobblemon-world-rpg-20261006`. The release manifest supplies the exact final commit and checksums. Historical handoff documents are preserved under `docs/handoff-reference`; production architecture uses normal source, native Cobblemon APIs and dedicated dialogue/shop payloads and screens.
