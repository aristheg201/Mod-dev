# Continuation checkpoint

Branch: feature/svarcade-production-20260912. Actions remain manual-only.
All 52 requirements remain mandatory; no production approval.

Added pure BoardOutcomeRules and MaterialRules. Definitions author repetition and
quiet-move claim/automatic thresholds, result reason IDs, material-count proofs,
opponent material constraints and square-class partitions. No named piece logic
is embedded in production Java. Tests cover common insufficient-material cases,
opposite-class and helpmate exceptions, mate priority, claim/automatic distinction,
and timeout/resignation when opponents lack possible mating material.

Reference semantics: FIDE 2023 Laws (5.1.2, 6.9, 9.2, 9.3, 9.6), and the
python-chess material checks documentation. Material proofs are conservative and
do not claim to solve every fortress/blocked-position dead-position problem.

Java 21 targeted checks pass. Full Gradle/JUnit/Fabric and final matrix have not
run here. Next: stateful adjudication/action wiring and bounded board bots; then
remaining TD and server/integration scope. Keep code-test-commit-push checkpoints.
