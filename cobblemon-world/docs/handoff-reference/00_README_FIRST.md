# CobblemonWorld — Codex handoff (2026-10-08)

This package consolidates the working CobblemonWorld RPG discussions, the available internal source overlays, the latest internal runtime JAR, the Cobblemon Armory dependency used for catalog auditing, and the shop visual reference.

## Canonical working target

- Repository: `aristheg201/Mod-dev`
- Branch: `feature/cobblemon-world-rpg-20261006`
- Minecraft: 1.21.1
- Fabric
- Java: 21
- Cobblemon: 1.8.1+1.21.1
- Fabric Loader: 0.18.4
- Fabric API: 0.116.17+1.21.1
- Mod id: `cobblemonworld`

**Important:** the full GitHub branch is the canonical full project. `source/legacy-clean-base-20261006` is an older clean source snapshot and is included only as a local baseline/reference. Do not replace the newer branch with it.

## Read in this order

1. `docs/CODEX_MASTER_PROMPT.md` — ready-to-paste execution prompt.
2. `docs/MASTER_SPEC.md` — consolidated product/engineering specification.
3. `docs/ACCEPTANCE_CHECKLIST.md` — pass/fail criteria.
4. `docs/SOURCE_STATUS.md` — exactly what is source, overlay, bytecode reference, and runtime reference.
5. `docs/NPC_PROGRESSION.md` — required NPC/story order.
6. `docs/SHOP_SPEC.md` — shop/UI/economy details.

## Source/reference contents

- `source/legacy-clean-base-20261006/`
  - older clean source snapshot from before the larger RPG/NPC expansion.
- `source/internal-overlays/dialogue/`
  - source-level branching-dialogue implementation and authored contact JSONs from the internal pass.
- `source/internal-overlays/shop/`
  - source used for the latest custom-shop experiment, plus the Armory catalog audit.
- `source/runtime-bytecode-reference/`
  - `javap -c -p` disassembly of runtime-only patch classes from the latest internal JAR. Use these to understand behavior, **not** as the final architecture.
- `artifacts/REFERENCE-CobblemonWorld-INTERNAL-CUSTOMSHOP-ECONOMYFIX-20261008.jar`
  - latest internal patched behavior reference. It is not the clean production implementation.
- `reference/cobblemonarmory-1.5.4-fabric-1.21.1.jar`
  - user-provided Armory dependency used to verify the fashion catalog.
- `reference/medieval-shop-ui-reference.png`
  - visual direction only. Do not copy third-party art pixel-for-pixel.
- `reference/base-runtime-qa-b4c5c5b8.zip`
  - previous known-green runtime QA baseline before the later direct patches.
- `transcript/previous-session.txt`
  - prior discussion context.

## Implementation policy

The final implementation must be clean source code on the canonical branch. Do **not** ship the reflection/bytecode direct-fix architecture as production. Replace it with normal Fabric networking, screens, services, registries, translation keys, and tests.

Build locally first. Do not burn time repeatedly triggering GitHub Actions for small edits. Run CI only when it is useful after local compile/remap/runtime smoke tests are green.
