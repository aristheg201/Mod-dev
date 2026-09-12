# Continuation checkpoint

Only feature/svarcade-production-20260912. Actions remain manual-only.
The complete 52-requirement production gate remains blocked.

Added generic BotProfile/BotDecisionSource/BotSystem. Exactly EASY/NORMAL/HARD
profiles are parsed from data; the scheduler has no difficulty-specific tuning.
Owner-thread scheduling shares the bounded worker service, respects delays and
retry/pending deadlines, issues expiring controllers, cancels stale/closed work,
and serializes assignments/attempt seeds/remaining delays, never live futures.
All results still enter ActionDispatcher. Session cleanup owns the scheduler.

Actually executed: Java 21 -Xlint:all -Werror BotSystemChecks passes, including
real core session/dispatcher scheduling and restore of delayed/in-flight work.
SearchChecks, BoardSearchChecks and BotWorkerChecks passed in preceding slices.
These are targeted tests, not full Gradle/JUnit/Fabric or release matrix evidence.

Next code: connect BoardDecisionSource and registered system/config factories,
using the runtime's hashed repetition-count snapshot (not replaying history on
the server thread), then finish remaining TD/Fabric/editor/integration scope.
No complete playable mod or production completion is claimed by this checkpoint.
