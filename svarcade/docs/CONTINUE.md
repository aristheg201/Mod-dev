# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Actions remain manual-only.
All 52 production requirements remain mandatory. No release approval.

Current capabilities: typed session services, lifecycle/security teardown,
deterministic PathSystem, spatial TargetingSystem, transactional CurrencySystem,
and pure immutable grid movement rules. Movement data defines ray/offset vectors,
occupancy, orientation, first-move constraints, protected roles, compound moves,
transient capture trails and promotion choices. Java has no named chess-piece or
Pokemon branches. Repetition keys preserve effective rights, not visual identity.

Targeted Java 21 tests pass, including initial-position perft through depth 4
(197281), Kiwipete through depth 3 (97862), sparse-position perft, castling through
attack rejection, pinned en-passant rejection, promotion, check/double-check,
checkmate/stalemate and effective repetition keys. Currency/path/security/targeting
checks also pass. Test-only BoardFixtures declares standard chess as data.
The pure rules are not yet a complete playable Chess definition or Fabric mod.
Full Gradle/JUnit/Fabric and production matrix remain unexecuted in this environment.

Next: wire generic board/movement sessions, history/adjudication/clocks and shared
action ingress; then continue remaining gameplay and server/integration scope.
Use local-test -> commit -> push. Do not audit or re-plan the repository.
