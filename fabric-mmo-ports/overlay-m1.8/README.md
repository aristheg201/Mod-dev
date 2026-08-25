# Fabric MMO ports overlay M1.8

MythicLib profile/session and cooldown parity slice reconstructed against MythicLib 1.7.1 bytecode.

Local Java 21 gate:

```text
MYTHICLIB_PROFILE_COOLDOWN_SESSION_RUNTIME=PASS
```

Locked semantics in this batch:

- cooldown keys are normalized consistently and replacement only occurs when the incoming cooldown should actually supersede the current one;
- cooldown reductions preserve initial duration, remaining duration and flat reduction semantics instead of treating cooldown as one disposable timestamp;
- profile lifecycle follows the original state machine: `CREATED -> OPENING -> OPEN -> CLOSING/ABORTING -> DEAD/DEAD_EARLY`;
- module readiness is tracked during opening and module close completion is tracked during shutdown;
- a profile switch requested while a transition is active is buffered and applied when the session reaches the corresponding safe state;
- temporary handler/session state is cleaned on shutdown;
- dead sessions are retained only for the original 24-hour timeout window before final cleanup.

Source archive SHA-256 after base64 decode:

`0f6385fd6225ed56057fc546d12d45bbed28bea8005ab9c76bff89549f2ec2a9`

Like M1.5-M1.7, this overlay is intentionally outside the current Actions path filter. No GitHub Actions workflow is started by this commit.
