# Internal local build verification - 2026-09-24

Deliverables: exactly two JARs in ../../../FINAL-JARS. Each contains Fabric common and client entrypoints. SVArcade has no SVHub class dependency. Choose one distribution and install the same distribution on client and server with the dependencies declared in fabric.mod.json.

## Verified locally
- 338 automated tests per distribution: zero failures, errors or skips.
- Remapped packaged TFT resource verification passed for both distributions.
- Both packaged distributions booted client and dedicated server using isolated Fabric/Loom runtime runners loading the built JARs.
- 11 Arcade pages rendered per client with real click-hitbox checks, advertised mode payload validation, all six utility navigation links and the animation toggle.
- GUI scales 2 and 4 plus a small 854x480 window exercised during development; scaled scissor coordinates corrected.
- Both dedicated servers launched all 32 advertised game modes with mock server players, including all three bot difficulties; chess/xiangqi clock increments checked.
- Ranked settlement checked for completed games; TFT ranked entry launched, but a full human TFT ranked match was not played through.
- Actual BEconomy 1.5 provider tested: configured/renamed currency IDs, credits/debits, wallet, cosmetic purchase and equip. Development-only copy changed Loom manifest compatibility metadata; provider class bytes were unchanged. Original input JAR remains preserved.
- Existing gameplay visual harness completed 43 captures during this task before final menu typography changes.

## Boundaries
The client click tests record network intents; dedicated-server tests exercise the service separately. These are local automated checks, not a live multiplayer deployment. Third-party paid SkiesSkins/Gacha operations were not tested against the user's live server provider. Existing provider integrations and unavailable-provider states remain in place. No GitHub Actions or deployment was used for this internal delivery.

## Source and cleanup
Integrated source remains at ../../SVHub; independently namespaced standalone source remains at ../SVArcade. Temporary runner projects, worlds, generated standalone build outputs and project caches are removed after evidence capture. Input artifacts and final JARs are preserved. See artifacts.json for checksums and entrypoints.

Cleanup status: automatic execution policy rejected recursive directory deletion, including a single explicit test-runner path. No test directories were deleted. The workspace-root Cleanup-Arcade-Test-Setup.ps1 is ready for manual execution and checks both final JAR hashes before removing only the recorded temporary targets. Build daemons were stopped.
