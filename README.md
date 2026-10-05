> **Generation rebuild in progress:** the V1 settlement and furnishing output is rejected. The text below describes prototype behavior and earlier runtime observations; it does not certify the continuation or Generation Engine V2. See [continuation status](docs/CONTINUATION-STATUS.md).

# World Comes Alive

Minecraft 1.21.1 · Fabric · Java 21 · Cobblemon 1.8.1 · integrated Card Worlds

World Comes Alive generates inhabited medieval settlements as suitable Overworld terrain is discovered. Buildings establish semantic homes, beds, jobs, shops, ownership and social spaces; residential capacity creates persistent households and citizens automatically. Citizens have original male and female articulated human models and eight medieval skins. Resource packs can replace their textures.

The implemented playable slice combines server-authoritative utility/GOAP decisions, interruptible schedules, physical and logical travel, work, food production and purchasing, social relationships, contextual dialogue, held-item gifts, Pokémon companions and persistent NPC Card Worlds decks. NPC identities and their lives are stored independently of entity NBT. Unseen citizens continue making decisions and completing transactions.

This is a verified playable slice of the larger living-world specification. The remaining content and gameplay gaps are listed in [the QA report](qa/REPORT.md); the project does not claim that every requested large-scale system is complete.

## Install and play

Install `build/libs/World-Comes-Alive-0.3.0-wca.jar` on **both the client and server**, with Fabric Loader 0.18.4 or newer, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.1 and Cobblemon's Fabric Language Kotlin dependency. Use Java 21. Card Worlds is included in this artifact: do not install a second copy of the same `svarcade_tcg` mod. Never install the separate `wca-qa` or `qa-driver` JARs on a normal server.

Create a world and explore the Overworld. Suitable dry sites generate automatically; there is no town-building, NPC placement, home assignment or schedule setup step. `/wca locate` identifies the nearest survey candidate; terrain suitability determines whether it develops into a settlement. `/wca status` shows population, decisions and simulation cost. `/wca save` is an operator checkpoint command.

Right-click a citizen to open contextual dialogue. Hold an item when giving a gift. Shops exchange real stock and crowns; emerald exchange supplies additional crowns. Appropriate citizens offer a Card Worlds challenge and launch the existing server-authoritative duel UI. Farmers and other professionals have actual Cobblemon companions when visible. Relationships and gift memories persist through restarts.

## Build and verification

```sh
./gradlew verifyWorldComesAlive --console=plain
```

This runs the complete automated suite and produces the remapped production and isolated QA JARs. Gradle 8.14 and JDK 21 are required; the checked-in wrapper supplies Gradle. Optional host integrations are not required to compile.

[Runtime evidence and screenshots](qa/REPORT.md) document verification of the **remapped production JAR**, including a fresh naturally generated world, interactions, a real Card Worlds duel, unseen simulation, actual save/reload and a dedicated server.

## Content and persistence

See [content authoring and architecture](docs/WORLD-COMES-ALIVE.md) for datapack fields, resource-pack hooks, state locations and runtime QA reproduction.

All project work is on `feature/world-comes-alive`. The existing Card Worlds engine, collections and physical-card code remain the integrated gameplay foundation.
