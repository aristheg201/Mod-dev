# Cobblemon World full scope

This branch is the single production line for the Cobblemon World RPG layer.

## Core progression
Persistent per-player campaign state, with no reset dependency. Level cap blocks send-out, battle use, capture, XP past cap, and player-caused natural spawns above cap. Existing over-cap Pokemon are never de-leveled or deleted.

## Trainer Phone
Apps: Trainer Card, Objective, Story, Side Quests, Level Cap, Badges, Contacts, Messages, League, Faction. All icons are first-party assets bundled inside the mod JAR. Notifications are top-right vanilla/Cobblemon-style toasts; the phone stores the full history.

## NPCs
Simple authored NPCs only. Place/move/remove by command; no mandatory schedules or simulation layer. NPC identity is data-driven and independent from placement.

## Story
The first phone contact is ???. Defeated major trainers/bosses become contacts and unlock scripted side quests/messages that gradually expose inconsistencies around ???. No AI dialogue is required.

The main-story twist: the player's victories remove seals rather than merely stopping anomalies. After the main story, ??? appears at a configurable fixed location. Phase one is a final Cobblemon trainer battle. On defeat, ??? reveals The One Below All and enters a second, direct RPG-combat phase.

## TOBA phase two
RPG skills: Dash, Guard, Break, Purge, Anchor, Partner. The boss requires a custom phase-one model plus a separate Blockbench-compatible phase-two rig and animations. Editable bbmodel source stays under dev assets; runtime geometry/textures/animations ship in the JAR.

## Cross-mode quests
Side quests may require Cobblemon battles, Card World battles and authored chess encounters where those mechanics serve the story rather than appearing as unrelated minigames.

## League and Factions
League progression is phone-visible. Factions is a first-party Cobblemon World subsystem stored by the mod itself; it does not depend on an external Factions mod. Players can create factions, invite/accept members, manage owner/officer/member roles, leave/disband, and use the Phone's Faction app. Every Saturday a large sky-island war opens in a dedicated dimension: Gate War -> Island Conquest -> Occupation. The owner holds the island until the next war and receives affinity-based Pokemon/rare/legendary spawn bonuses. Island template, affinity, boosts, final-boss coordinates and reward pools are config/data driven.

## QA contract
QA runtime must capture the built JAR in Minecraft, including every phone app, custom icons, toast queue, NPC placement, trainer battle start, ???, TOBA transformation/phase two, faction island states, persistence/reconnect, over-cap negative cases and visual defects such as floating feet/clipping/backwards facing.
