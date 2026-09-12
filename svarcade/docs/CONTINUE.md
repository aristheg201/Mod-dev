# Continuation checkpoint

Only feature/svarcade-production-20260912. Actions remain manual-only.
The single complete 52-requirement release remains blocked.

BoardDecisionSource now binds real session Board/Movement/Adjudication capabilities
to compiled immutable BoardBotStrategy workers. Snapshot repetition counts match
the runtime's SHA-256 representation and are cached by immutable board identity;
no move-history replay occurs on the server thread. Worker outputs map to the
same move/claim/offer action handlers and clock transactions used by humans.
Unregistered bot action bindings are rejected. Draw-offer acceptance is tunable.

Actually executed Java 21 -Xlint:all -Werror checks: BoardBotSessionChecks for
EASY/NORMAL/HARD real core matches, legal moves and clock increments, intended
claims without moving, mate, draw offers, permission/phase/expiry and pending-work
restart. SearchChecks, BoardChecks (original perft), BoardSearchChecks,
BotSystemChecks and BotWorkerChecks were recompiled and passed too.
Full Gradle/JUnit/Fabric/in-world/compatibility/load suites are NOT claimed run.

Next: finish generic action-service/system factory composition and packaged
configuration so production bootstrap need not hand-wire test factories, then
remaining TD/Fabric/editor/rewards/recovery/integration/UI requirements.
Board bot core integration exists; full playable Pokemon Chess is not yet complete.
