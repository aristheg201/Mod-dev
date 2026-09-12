# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Actions remain manual-only.
All 52 requirements are still mandatory; this checkpoint is not release approval.

Added reusable BoundedSearch: iterative minimax, alpha-beta, move ordering, bounded
transposition table, quiescence with forced evasions, legal root fallback, seeded
randomness, time/node budgets and distinct exhaustion versus cancellation.
SearchChecks passed using javac/java 21 with -Xlint:all -Werror. This includes 200
random layered DAGs comparing alpha-beta/TT against unpruned minimax, plus explicit
budget, interruption, horizon, forced-evasion and fallback regressions. A JUnit
bridge is included; Gradle/JUnit itself was not run in this network-isolated turn.

Existing board adjudication, clocks, draw offers/claims, resignation, path,
currency and targeting are retained from parent 29ef5d9. Next: connect search to
immutable board state and actual bot/session/config scheduling, including preserving
last completed search results on time exhaustion. Do not claim game bots complete
until this wiring and same-dispatcher runtime tests exist. Then continue remaining
TD/Fabric/editor/integration/persistence/reward/UI scope. No Actions dispatched.
