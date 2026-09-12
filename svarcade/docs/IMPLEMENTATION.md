# Implementation status and continuation contract

The authoritative scope is SCOPE.md, all 52 sections in one production release.
This document describes implemented mechanisms and remaining acceptance work,
not a product roadmap or permission to ship incomplete features.

## Implemented mechanisms in this branch

- Independent Java 21 core module; no Minecraft, Cobblemon or server-mod dependencies.
- Namespaced frozen capability registries and immutable bounded config values.
- Safe YAML package loader with duplicate/tag/alias/type/schema/reference/dependency
  validation, file/package/depth/event limits, UTF-8 validation and path confinement.
- Complete-candidate atomic registry replacement, latest-request-wins ordering,
  missing-integration availability and pinned old definition snapshots.
- Definition-authored FSM, pure action reducers, registered predicates, enter/exit
  atomicity, event/deadline transitions and explicit state restore without replay.
- Generic session/runtime system composition; exclusive tokenized arena leases,
  participant uniqueness, owner-thread mutation, explicit versioned system state.
- Reverse cleanup that retries failed resources and retains their arena/session
  ownership until cleanup succeeds. Bounded failure history and runtime timing.
- Bounded deadline timers, replacement/cancellation and per-advance callback limits.
- Shared intent ingress for membership/arena/permission/range/controller/replay/
  revision/rate checks; registered game-system rules validate additional constraints.
- Bounded bot worker/queue, immutable visible state/tuning, operation/time budgets,
  cancellation and stale-decision rejection. Strategy authors must check budgets;
  arbitrary uncooperative Java extensions are not safely preemptible in-process.
- Versioned typed state codec with size/depth/type/checksum guards, async bounded
  single-writer atomic state-file persistence, explicit session recovery envelopes.
- RAM editor transaction with immutable preview, bounded undo, selection reset,
  cancel, validation and explicit asynchronous publication callback.
- A 52-requirement evidence manifest and a fail-closed production gate. The
  workflow is manual-only; no checkpoint push creates an Actions run. Do not
  dispatch it until the entire scope and final full-matrix workflow are complete.

## Integration contracts that must be respected

Create runtime/thread guards on the server's authoritative thread. Definition
loading and AtomicStore construction require an I/O/bootstrap thread; never invoke
those file operations in server tick/interaction handlers. AtomicStore futures
complete on the I/O worker and must be marshalled before touching game/world state.
Systems must mark session revision dirty on authoritative game-state changes.
System constructors must register resources immediately with the session tracker
and `close()` must tolerate partially initialized/restored state.

FSM action/condition functions are pure over immutable data. World effects require
separate tracked effect execution; they must not be hidden inside a reducer or
replayed by recovery. `IntentGate.validate()` consumes accepted sequence numbers;
apply the validated operation immediately on the same owner thread through the
appropriate system transaction. Facts come from server state, never client claims.

The runtime keeps sessions with failed startup/cleanup reachable. An adapter must
retain that runtime and drive cleanup/recovery; simply constructing a GenericSession
and discarding an exception loses that ownership guarantee. Recovery validates
exact definition fingerprints and rejects unsupported state schemas; this does not
yet restore Minecraft player inventories, world entities or external resources.

EditorSession's publisher contract is implemented/tested with AtomicStore, but the
actual world tools, previews, YAML arena publication and explicit-save UI remain
unfinished. It is not an in-world Admin Wand yet.

## Outstanding work within the SAME release

Generic board/movement/rules/combat/path/wave/deployment/upgrade/shop/objective/
currency/rendering/interaction systems; complete pokemon_chess and pokemon_td
packages; all game-specific acceptance rules; minimax and TD strategies with the
three data-tuned difficulties; party snapshot adapter and canonical identity;
in-world controllers/editor; Fabric bootstrap/commands/UI/messages and player
protection; queues/matchmaking/spectators/disconnect; typed durable reward adapters;
stats/leaderboards; optional integration loaders; dirty persistence orchestration,
player/resource rebinding, restart recovery policies; complete metrics/diagnostics.

All feature suites, full adapter compatibility matrix, fault-injection/restart,
real-server gameplay, and representative load/performance evidence must still be
completed. A core library JAR is not the requested production mod artifact.

## Verification

Java 21 compilation uses UTF-8, all compiler lint warnings and warnings-as-errors.
JUnit runs exercise adversarial loading/reload, FSM correctness/atomicity/recovery,
ownership/cleanup failures, scheduler bounds, security, state corruption, real
atomic filesystem writes/reopens, editor transactions and bounded/stale bot work.
The tests intentionally run without Minecraft/Cobblemon on their classpath.

Use `gradle :core:check :core:jar` from `svarcade/`. Detailed evidence is generated
under `core/build/test-results` and retained by the SVArcade workflow. Production
release requires `python3 tools/release_gate.py` to pass; it currently must fail.

Checkpoint rule: local targeted tests → commit → push this branch → continue.
Never use Actions in this development loop; retain clean commit messages.

References use a sole `$ref: "package-file.yml#/json/pointer"` mapping; filenames
are package-root-relative, `#/pointer` stays in the current file. References are
expanded before schema checks; transitive files participate in the fingerprint.
System dependencies are compiled into stable topological start order; cycles fail
reload before publication.
