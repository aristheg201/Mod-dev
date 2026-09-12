# SVArcade — one complete production scope

Authority: Aris's 52-section specification of 2026-09-12. This document supersedes
conflicting earlier designs. All 52 requirements belong to the SAME first release.
There is no MVP, intentional missing feature, or deferred completion release.
Development commits are work in progress, not separately releasable product stages.
Future versions may fix defects, support new dependencies, or add new content.

**Java defines WHAT CAN EXIST. Config defines WHAT THE GAME IS.**
The generic core must not select game implementations by game ID/type, species,
wave number, or difficulty. No `ChessGame`, `TowerDefenseGame`, or renamed equivalents.
Chess/TD are immutable definition packages composed from generic capabilities.
Removing a definition folder removes that minigame; composing existing primitives
into a new minigame must not require recompilation.

## Binding acceptance requirements

1. Generic engine, systems and registries compose minigame definitions, with no game-specific Java dispatch.
2. Core includes DefinitionLoader/Registry, schema/semantic validators, GenericGameRuntime/Session, Participant/Team/ArenaRuntime, StateMachine/Timer runtime, ResourceTracker/Cleanup, Persistence/Recovery, Security/RateLimiter, Bot/Editor runtime, Reward/Action/Condition/Rule/System/BotStrategy/EditorTool registries, Metrics/Diagnostics.
3. Reusable Board, Turn, Piece, Movement, Path, Wave, Combat, Targeting, Deployable, Upgrade, Shop, Objective, Currency, Renderer, Interaction, Spectator, Queue, Matchmaking, Stats and Leaderboard systems.
4. States, transitions, conditions, enter/exit actions and timers are data-driven; no hardcoded game lifecycle.
5. Full config tree: config.yml; en_us/vi_vn messages; cobblemon/svquest/svframe/luckperms/economy integrations; shared effects/status-effects/reward-types/bot-strategies; per-minigame folders and arenas. Chess files: game, states, rules, board, pieces, bots, editor, ui, rewards. TD files: game, states, combat, pokemon-profiles, move-effects, upgrades, enemies, waves, bots, editor, ui, rewards.
6. game.yml composes namespaced system IDs and config references, player limits and arena directory; no type-to-game-class switch.
7. Complete chess: legal movement, capture, check/double-check, mate/stalemate, castling, en passant, promotion, threefold repetition, 50-move rule, insufficient material, draw offers, resignation, clock/increment/timeout, move and position histories. Include perft-style verification.
8. Chess movement authored from generic ray, offset, adjacency, forward, capture and condition primitives.
9. Logical KING/QUEEN/ROOK/BISHOP/KNIGHT/PAWN roles are independent of Pokémon renderer species and stats.
10. Chess players remain on off-board stages; authenticated server-validated controller: right-click select/destination confirm, sneak-right-click cancel.
11. Bots are first-class Participants through BotRuntime/Controller/DecisionContext/Strategy/ThinkBudget. Exactly EASY/NORMAL/HARD standard difficulties.
12. Difficulty tuning lives in bots.yml. Search depth, randomness, alpha-beta, move ordering, transposition and quiescence are authored data, never difficulty branches in core.
13. Bots obey the same rules, information, currency, placement, caps and cooldowns. Hard improves decisions only; never free resources or hidden information.
14. Pokémon TD is a complete definition composing FSM, path, wave, combat, deployment, currency, shop, upgrade, bot, objective, renderer and editor systems.
15. TD towers originate in a validated real-party snapshot, with ownership and canonical Pokémon identity. One Pokémon cannot be deployed twice.
16. Never permanently mutate party level, XP, IV, EV, nature, moves, ability, held item or friendship; all TD power lives in disposable runtime state.
17. Profiles derive from species/form/aspects/types/level/moves/ability. Level normalization and clamp are authored data (example Lv1≈0.90, Lv50≈1.00, Lv100≈1.10).
18. Actual moveset affects the profile through move-effects.yml (e.g. direct electric damage, control, water AoE, ice slow), without reproducing full battle formulas.
19. TD Coins exist only in the session; server economy cannot purchase deployment or upgrades.
20. Data-driven costs and permissions for deploy/move/recall/upgrade/targeting/sell.
21. Mandatory PREPARE→WAVE→WAVE_CLEAR→SHOP loop, including planning, deployment, relocation and power-ups between waves; power expires at match end.
22. Upgrade composition supports generic stats, type/archetype effects and species/move-specific pools entirely in data.
23. Generic damage/armor/resistance/crit/range/cooldown, projectile/instant/AoE/chain, DoT/slow/stun/buff/debuff, durations/stacking/immunity/resistance.
24. Enemy renderer, health, armor, speed, resistances, traits, reward and tags are data; no hardcoded species bosses.
25. Wave groups, references, count, intervals, lane, modifiers, elite/boss tags, clear rewards and shop behavior are data.
26. Deterministic waypoint path progress is authoritative for TD; no vanilla freeform pathfinding core.
27. Target modes FIRST/LAST/CLOSEST/FARTHEST/STRONGEST/WEAKEST/LOWEST_HP/HIGHEST_HP and flying/ground/boss/tag/type/status filters are available as configured.
28. TD EASY/NORMAL/HARD bots deploy, upgrade, move, recall, retarget and reserve currency. Config controls mistakes, composition/coverage/synergy, multi-wave/upgrade/placement planning and boss preparation, within the same rules.
29. TD supports solo, solo+bot, co-op, players+bot fill, endless, boss waves and spectators. Player/team/tower caps and scaling are data.
30. Generic in-world Admin Wand tools: point, region, multi-region, path, direction, object, preview, undo, save, cancel; definition-authored wizard.
31. Chess editor selects board region, two player stages, spectator and orientation.
32. TD editor selects spawn/goal/path/build zones/player stages/spectator by world clicks, without requiring typed coordinates.
33. Editor transactions remain in RAM through preview/validation; explicit save writes atomically; undo/cancel/reset selection required. No config writes per click.
34. Multiple arenas and sessions per definition; each arena has at most one active session, including recovery/cleanup transitions.
35. Typed item/XP/currency/stats/SVQuest/permission/custom reward providers. No arbitrary console-command reward engine. Durable idempotent claims across crash/restart.
36. Optional Cobblemon/SVQuest/SVFrame/LuckPerms/Economy/Placeholder adapters isolate missing/incompatible mods and fail cleanly.
37. Core boots without Cobblemon. Definitions declare required integrations; missing dependencies disable those definitions only.
38. Parse→schema→semantic→reference resolution→immutable candidate→atomic registry swap. Any invalid candidate retains old registry; live sessions retain old definition snapshots.
39. Versioned explicit system serialization covers identities, arena, participants/teams, FSM/timers/currency, board/history/clocks, waves/enemies/path progress, deployments/refs/upgrades. Never arbitrary object graph serialization.
40. Recovery validates definition/version, rebinds players/resources and resumes safely, or aborts/restores/releases/cleans. No ghost sessions or duplicate resources.
41. Data-driven disconnect grace and reconnect policies: chess forfeit after grace; TD retained towers and bot takeover or cleanup after timeout.
42. Snapshot exactly the player state modified by SVArcade: position, gamemode, temporary inventory/controller, movement restrictions, spectator/effects as relevant. Restore on exit/crash/cleanup; never wipe inventory.
43. Spectators join/leave/view safely, cannot mutate game state or attack/interact, and regain prior state. Availability/cap are definition data.
44. Generic compatible queues/matchmaking, free arenas and configured bot fill. Chess direct challenge/PvP/PvE; TD solo/co-op queues/bot fill.
45. Persistent generic stats and leaderboards; definitions choose metrics. Chess games/results/difficulty/rating; TD games/wins/highest wave/boss kills/damage/upgrades/solo-coop/bot difficulty.
46. Authoritative intent validation: membership, arena, phase, turn, controller, range, resource owner, currency, upgrade prerequisites, placement, permissions and rate. Bots use the same relevant validation path.
47. No server-thread disk/network I/O, unbounded bot computation, per-tick saving, full-world scans, freeform TD pathfinding or every-tower×every-enemy per-tick scans. Use spatial index, cached candidates, attack deadlines, dirty persistence, bounded off-thread pure work and authoritative server-thread application.
48. Session ownership for entities/displays/timers/tasks/bossbars/items/listeners/visuals. Idempotent cleanup leaves zero resources/tasks/listeners/arena locks. Failures stay observable and retryable.
49. /sva debug session/arena/player/bot; bounded diagnostics and metrics for sessions/arenas/bots/think time/enemies/towers/targeting/cleanup/recovery/config/rewards/tick costs without log spam.
50. No hardcoded player-facing text. en_us and vi_vn catalogs, MiniMessage/placeholders, actionbar/bossbar/title/sound/particle presentation.
51. Automated core/chess/bot/TD/editor tests cover all listed behavior, security and recovery failures. Compatibility matrix: supported core alone, Cobblemon, each optional adapter, supported combinations, missing/incompatible mods and missing-required-integration definitions. Failure isolation is mandatory.
52. ONE release gate: every requirement complete; both full default packages, every mode and every bot difficulty, complete editor/UI/protection/recovery/security/observability, tests and load/performance gates green, no known blocker or critical defect. No absolute future-bug-free claim; no release with intentional missing functionality.

## Evidence discipline

`release-requirements.json` records status and evidence by requirement number.
A source file, passing compilation or test name alone does not prove an entire
requirement complete. `in_progress`/`pending` blocks the production release gate.
Tests of pure core code do not substitute for actual Minecraft/Cobblemon server
integration, adapter matrix, fault injection, or representative load tests.
