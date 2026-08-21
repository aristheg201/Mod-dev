# Lively implementation status - dev.1

Implemented in core:

- offline domain-specific utility cognition
- immutable NPC/world snapshots
- bounded prioritized AI scheduler with duplicate suppression
- stale-revision protection before main-thread apply
- server-authoritative typed-action validation
- lightweight Vietnamese/English NLU intent/entity extraction
- chat-isolated HYBRID dialogue session model
- rotating nonce protection for clickable dialogue choices
- causal quest proposals with bounded reward/amount validation
- game-agnostic Combat Cortex with bounded predictive search
- stable CombatAdapter API for external battle systems

Implemented in Integration:

- optional Cobblemon detection
- opt-in Cobblemon NPC interaction using entity command tag `lively`
- initial read-only reflective Cobblemon battle bridge scaffold

Not yet considered complete:

- direct version-specific Cobblemon battle action binding
- full Cobblemon party/move/ability/item/field snapshot mapping
- persistent NPC memory/database
- GOAP/HTN navigation planner and asynchronous path graph
- economy/faction/story simulation
- production config/schema/migrations
- security/fuzz/soak suite

The reflective Cobblemon combat bridge deliberately refuses mutation until a verified typed binding exists. Guessing an upstream battle method would violate the authority/security contract.
