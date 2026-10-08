# Source status and provenance

## Canonical full source

The canonical complete project is the GitHub repository/branch:

- `aristheg201/Mod-dev`
- `feature/cobblemon-world-rpg-20261006`

Codex should begin from that branch and preserve the systems already working there.

## `source/legacy-clean-base-20261006`

This is the earlier clean source ZIP that was previously handed off. It is useful for known-good simple source and build metadata but predates much of the expanded story/NPC/faction/phone runtime. **It is not the latest whole project.**

## `source/internal-overlays/dialogue`

These are real Java source files/resources from the internal dialogue-tree pass:

- `DialogueRuntime.java`
- `DialoguePhoneUi.java`
- `TrainerSkinAudit.java`
- branch/contact JSON content
- transformer helper used during that emergency pass

Port the behavior into clean source; do not retain the JAR transformer as the production mechanism.

## `source/internal-overlays/shop`

`DirectShopHud.java` and `DirectShopServer.java` are the exact source used for the latest custom-shop experiment. They demonstrate:

- shop state/layout behavior;
- real ItemStack rendering attempts;
- Armory catalog audit/pricing;
- BeastCoin server-side purchase direction.

They also contain reflection/emergency-patch compromises. Treat them as behavior/reference code, not final design. Production should use dedicated typed classes/network payloads.

`catalog_java.txt` contains 204 generated candidate entries from the provided Armory JAR. Do not hardcode the count in production; discover/generate from registry.

## `source/runtime-bytecode-reference`

Some later runtime fixes were only available as compiled classes in the patched internal JAR. The included `javap -c -p` dumps document the actual bytecode behavior of:

- `CompassRuntime`
- `DirectClientFixes`
- `DirectServerFixes`
- `ServerCommandRuntime`
- latest compiled dialogue/shop classes

Use these only to recover intent when the clean branch differs. Reimplement them normally.

## Runtime reference JAR

`artifacts/REFERENCE-CobblemonWorld-INTERNAL-CUSTOMSHOP-ECONOMYFIX-20261008.jar` is included so Codex can compare class/resource contents and behavior assumptions. It is not a release candidate.

## Why the package is structured this way

Several fixes were made as direct bytecode/local patches after the clean branch because previous build iterations were slow. The final job is to **merge the useful behavior back into the clean source branch** and remove the emergency architecture.
