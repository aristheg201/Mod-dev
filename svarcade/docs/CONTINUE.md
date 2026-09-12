# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Actions remain manual-only.
All 52 production requirements remain mandatory; no production approval.

Added TurnSystem with configurable monotonic time banks, increment, pause/resume,
commit-time expiry checks, explicit remaining-time restore and periodic persistence
dirtiness. GenericSession now separates persistence dirtyVersion from action revision,
so temporal checkpoints do not automatically invalidate legal pending intents.
Clock expiry is revalidated at action commit. BoardMoveHandler composes board and
clock changes atomically; CompositeChange rolls back only completed components.

Local Java 21 checks pass: BoardRules/perft, BoardSession, Currency, Path, Security,
Targeting and Turn. Turn tests include signed nanoTime wrap, elapsed-time rollback,
expiry between prepare/apply, real human/bot board+clock transactions and restart.
No Actions dispatched. Full Gradle/JUnit/Fabric/compatibility/performance acceptance
has not run here; default complete games and server bindings are still mandatory.

Next: objective/result primitives, data-defined draw/mate/resign adjudication,
bounded board bots; continue TD and server integration rather than re-auditing.
Use targeted local-test -> commit -> push checkpoints on the same branch.
