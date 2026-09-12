# SVArcade

Generic, definition-driven minigame platform targeting Minecraft 1.21.1 / Java 21.
Authoritative acceptance scope: [all 52 requirements](docs/SCOPE.md).

This branch is under development and is **not a production release**. Chess, TD,
Minecraft integration and the full release evidence must all be complete before
release. Development order does not divide the product into separate releases.

The independent `core` Gradle module has no Minecraft or optional-mod classpath.
It allows deterministic tests and verifies that engine data/runtime mechanisms do
not depend on Cobblemon or a particular server's modpack. Game content belongs in
definition folders; Java supplies reusable capabilities.

Build from this directory with Java 21 and Gradle 8.10.2:

```sh
gradle --no-daemon :core:check :core:jar
python3 tools/release_gate.py
```

The second command intentionally rejects release while any required feature or
release evidence is incomplete. Ordinary CI tests may pass during development;
that is not production approval. The current core JAR is not a loadable Fabric mod.

Existing CobbleBR bootstrap files at the repository root are independent; SVArcade
builds from `svarcade/` and does not unpack or run that unrelated source archive.
