# NPC progression / placement order

Use this as the intended story order. Placement coordinates are authored by the server/map builder using `/cworld npc place <id>`; do not hardcode quest coordinates into visible text.

## 0. Town 1 / early game

Service NPCs:

- `professor_hale`
- `daycare_mira`
- `pokemall_ren`
- `bicycle_tomo`
- `fashion_elle`

First major trainer:

- `mara_voss`

Flow: Professor Hale / early gameplay / phone/resource objectives -> Mara Voss.

## 1. Town 2

- `dr_orin`

Flow: Mara defeated -> Town 2 progression -> Dr. Orin.

## 2. Town 3 / Black Card

- `rook`

Flow: Orin -> Town 3 -> Rook -> Black Card.

## 3. Town 4

- `selene_kade`

Flow: Rook / Black Card -> Town 4 -> Selene Kade.

## 4. Town 5 / floating-jungle area

- `sixth_warden`

Flow: Selene -> Town 5 -> Sixth Warden.

`Warden` is a trainer/title here; do not restore old visible mystical "six seals" exposition.

## 5. Town 6 / metropolis / Team Rocket base / laboratory

Physical order inside the base:

1. `rocket_grunt_01`
2. `rocket_grunt_02`
3. `rocket_grunt_03`
4. `rocket_admin_vex`
5. `lab_scientist_iris`

Flow: enter base -> Grunt A -> Grunt B -> Grunt C -> Admin Vex -> research/lab access -> Dr. Iris Vale -> School of Wolf clue.

## 6. Town 7 / Archaeologist + Seventh Warden

- `archaeologist_marlow`
- `seventh_warden`

Marlow must come before Seventh Warden.

Flow: Black Card + lab clue -> Dr. Gideon Marlow -> Black Card registry identified -> Seventh Warden.

## 7. Town 8 — currently missing in the old content

This gap must be implemented. Do **not** route Seventh Warden straight into Battle Tower as if the eight-town circuit were complete.

Required shape:

Town 8 local progression -> dedicated Town 8 final qualifier -> eight-town circuit complete -> Battle Tower unlocked.

Keep it data-driven. Add the necessary Town 8 NPC/trainer definition(s), authored dialogue, battle team(s), flags/objectives, skin validation, and placement ids. At minimum there must be a dedicated final qualifier. Do not fake this with an invisible flag jump.

## 8. Battle Tower

1. `battle_tower_receptionist`
2. `battle_tower_trainer_01`
3. `battle_tower_trainer_02`
4. `battle_tower_trainer_03`

Flow: Receptionist/Mina -> Tower Ace Rowan -> Tower Ace Nyx -> Tower Ace Orion -> Battle Tower Qualified.

## 9. Royal League

1. `royal_league_receptionist`
2. `league_elite_01`
3. `league_elite_02`
4. `league_elite_03`
5. `aurelia`

Flow: Steward -> Elite Cassian -> Elite Seraph -> Elite Kael -> Champion Aurelia -> Royal League Complete -> School of Wolf unlocked.

Aurelia comes after all three Elite members, not as an ordinary map trainer inserted mid-chain.

## 10. School of Wolf / final area

1. `school_wolf_gatekeeper`
2. `school_wolf_trainer_01`
3. `school_wolf_trainer_02`
4. `school_wolf_trainer_03`
5. `school_wolf_master`

Flow: Bran/Gatekeeper -> Fen -> Skoll -> Hati -> Master Vargan -> School trials complete -> TOBA record -> final story.

## 11. Final `???`

`mysterious` is story-spawn-only. It must not require manual `/cworld npc place mysterious`.

The encounter is triggered only after the School of Wolf + TOBA record conditions are satisfied.

## Legacy actor to remove from active roster

`resonance_heart` is compatibility residue from an older story. Do not include it in the active main-story placement list.

## Spawn/admin commands

- place: `/cworld npc place <id>`
- rotate: `/cworld npc rotate <id>`
- inspect: `/cworld npc list`

Placement/rotation must normalize pitch to zero and persist the authored yaw.
