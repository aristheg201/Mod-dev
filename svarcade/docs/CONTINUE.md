# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Keep Actions manual-only.
All 52 requirements remain mandatory; production gate remains blocked.

BoardAdjudicationSystem now composes Board, Movement, Objective and Turn capabilities.
Human/bot actions support current-position and intended-legal-move draw claims,
draw offer/accept/decline, resignation, automatic repetition/quiet limits and
material-aware timeout. Move, history, clock, offer state and outcome are prepared
as one flat transaction. Terminal moves freeze clocks in the same commit; a claim
by intended move ends the match without executing that move. Offers survive their
owner's move and restart, and are consumed by the opponent's move transaction.

Java 21 targeted tests pass for shared ingress, all these results, rollback on
commit-time expiry, restart consistency, perft, currency, paths and targeting.
No Actions dispatched. This does not claim completed Fabric/UI/bots/TD/rewards,
full JUnit/Gradle or final compatibility/load evidence.

Next: bounded board search strategies and actual bot/session/config wiring, then
remaining TD/server/integration scope. Continue code-test-commit-push, no re-audit.
