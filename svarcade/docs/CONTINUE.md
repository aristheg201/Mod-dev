# Continuation checkpoint

Only feature/svarcade-production-20260912. Actions remain manual-only.
All 52 requirements remain mandatory; production gate remains blocked.

BoundedSearch is now connected to immutable GridPosition through BoardSearch.
Evaluation weights and optional team/piece-square tables are compiled from data.
The strategy uses the same MovementRules, MaterialRules and BoardOutcomeRules as
runtime, including automatic draws, current-position and intended-move claims.
TT keys include quiet counters and exact path repetition deltas; root history is
shared immutable data. Search has no world/player/party access or mutation path.
Movement/material evaluation now accept deadline/cancellation checkpoints inside
bounded loops; original non-search APIs are retained.

Actually executed with Java 21 -Xlint:all -Werror: SearchChecks, BoardSearchChecks,
and the original BoardChecks. Perft remains 197281 at start/depth4 and 97862 at
Kiwipete/depth3. Board search tests cover legal fallback at depths 1/3/5, mate in
one, pinned pieces, en passant, promotion, configured castling preference, draw
claims, insufficient material, immutable input and inner-loop cancellation.
Full Gradle/JUnit/Fabric and final compatibility/load matrix were NOT executed.

Next code: bot runtime/session/config wiring and result handling for exhausted
search, then remaining TD/Fabric/editor/integration/persistence/reward/UI scope.
Neither playable Chess nor the complete bot feature is claimed complete yet.
