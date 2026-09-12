# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Keep Actions manual-only.
All 52 requirements remain mandatory. The production release gate stays blocked.

MovementSystem and BoardSystem now compose typed capabilities. Board state owns
compact full move history, effective repetition counts and atomic prepared moves.
BoardMoveHandler sends humans and bots through the same ActionDispatcher with
phase, turn, legality, replay and event-backpressure checks. Dispatcher resources
are now session-owned: queued events, bot controllers and grants clear on close.
Board snapshots validate dimensions, references, counters and final history key.
Full history replay is a pure off-thread operation with caller-supplied budgeting.

Local Java 21 standalone checks all pass: BoardChecks (perft and special rules),
BoardSessionChecks (real dispatcher/composition/history/recovery), CurrencyChecks,
PathChecks, SecurityLifecycleChecks and TargetingChecks. Full JUnit/Gradle/Fabric
and production integration/load evidence have not run in this environment.
No GitHub Actions invocation. This is not a playable complete Fabric release yet.

Next: generic turn clocks, adjudication/draw/resign and bounded board strategies;
continue TD combat/deployment/waves and the remaining server/integration scope.
Code -> targeted local test -> commit -> push. Do not audit/re-plan the repository.
