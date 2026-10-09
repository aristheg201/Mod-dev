#!/usr/bin/env python3
"""Generate reviewer/player-map documents from the exact shipped content."""
import json,pathlib,collections
root=pathlib.Path(__file__).resolve().parents[1];r=root/'src/main/resources';w=json.loads((r/'data/cobblemonworld/narrative/world.json').read_text());en=json.loads((r/'assets/cobblemonworld/lang/en_us.json').read_text());vi=json.loads((r/'assets/cobblemonworld/lang/vi_vn.json').read_text());d=root/'docs/narrative';d.mkdir(parents=True,exist_ok=True)
name={a['id']:a['name'] for a in w['actors']};name.update({p['id']:en[p['name']] for p in w['pois']})
def write(file,text): (d/file).write_text(text.strip()+'\n')
def stage_table(stages):
 out=['| # | Stable stage ID | Mechanic | Target | Objective |','|---|---|---|---|---|']
 for i,s in enumerate(stages,1):out.append(f"| {i} | `{s['id']}` | {s['type']} | {name.get(s['target'],s['target'])} | {en[s['objective']]} |")
 return '\n'.join(out)
write('MAIN_STAGES.md','# Main campaign: 69 stages\n\n'+stage_table(w['campaign']))
flow=['# Campaign flow\n','Seven Acts group 69 stages into chapters. Stages advance only from their own matching conversation, completed battle, inspected authored point, service, inventory delivery or an authored service. Purchases are optional side activities.','New saves begin with Hale. Legacy flags infer completed preceding stages without discarding contacts, messages, quests, placements or faction data.']
for act,ss in __import__('itertools').groupby(w['campaign'],key=lambda s:s['act']):
 ss=list(ss);flow.append(f"\n## Act {act}\n\n"+' → '.join(en[s['title']] for s in ss)+'\n\n'+stage_table(ss))
flow.append('\nThe eight-town qualifier precedes False Victory and the Tower. Tower registration precedes Rowan, Nyx and Orion. League registration precedes Cassian, Seraph, Kael and Aurelia. Aurelia’s aftermath precedes Bran and the School. Vargan grants archive access; all three records, Hale’s account and the final call remain separate objectives. Winning the final battle opens the explanation and restoration scenes; the epilogue unlocks postgame.')
write('CAMPAIGN_FLOW.md','\n\n'.join(flow))
side=['# Substantial side stories\n','43 regular chains, one optional Ren tutorial, and the 24-stage seasonal story. The Ren tutorial has one stage and zero currency reward. The one-time regular reward is 20 BeastCoin; an optional ten-BeastCoin scam is separately refunded on completion.','A chain can be accepted at its giver or through the phone’s Pin to Objective action when prerequisites hold. Pinning accepts an available chain, records its stable stage ID and immediately sends navigation.']
for c in w['chains']:
 if c['id']=='weather_duo_01':continue
 side.append(f"## {en[c['title']]} / {vi[c['title']]}\n\nGiver: {name[c['giver']]}. Prerequisite: `{c['prerequisite']}`. {len(c['stages'])} stages. Reward: {c['reward']} BC once.\n\n"+stage_table(c['stages']))
write('SIDE_CHAINS.md','\n\n'.join(side))
c=next(c for c in w['chains'] if c['id']=='weather_duo_01')
write('WEATHER_DUO.md',f'''# Weather Duo: season weather_duo_01

Prerequisite: completed campaign. Giver: {name[c['giver']]}. 24 stages across forest stations, archives, harbor, geological chamber and sea shrine.

The false alerts originate in two separated modules of a Pokémon-refuge monitoring system. Groudon and Kyogre answer incompatible rescue calls. The story repairs the source of those calls and retains evidence of the removal, rather than recreating a land-versus-sea war.

{stage_table(c['stages'])}

The first two sightings use actual Cobblemon Pokémon models. Sightings cannot be captured or challenged, expire, and are removed on shutdown. Final acquisition is a guaranteed, visible partnership reward after both guardian battles and restoring balance. It uses Cobblemon storage directly, with separate one-time entitlements for both species. It is not a wild capture reward.

Reward policy: level 70; non-shiny; Adamant/Drought Groudon or Modest/Drizzle Kyogre; six IVs of 25; four authored moves. A full party sends the reward to PC. A reserved UUID is persisted before grant; a delivered entitlement never recreates a released or traded Pokémon. Unavailable/full PC leaves the reservation retryable. There is no cross-database atomic transaction between Cobblemon storage and the progression JSON: a process crash at that boundary requires reconciliation rather than an automatic duplicate grant.

The `seasons` and `rewards` records in `world.json` support future season IDs, prerequisites, chains, Pokémon properties, IV policy and guardian requirements. Reward IDs are unique across the loaded definitions; old entitlements remain in saves when a season definition changes.
''')
teams=['# Trainer teams and difficulty\n','30 authored rosters, including Mara’s six-member postgame upgrade. All permanent stats default to 31 IV. The only overrides are explicit zero Speed for slow Trick Room/Gyro Ball builds and zero Attack on specific special attackers. Early EV investment rises from 20–25% to 40%, 65%, 80%, then complete optimized spreads. Tower onward uses complete spreads.','Levels match the challenger’s strongest party member and preserve small authored roster gaps. No hidden damage or speed bonuses are added. NPC skill is 5, using Cobblemon’s StrongBattleAI. The difficulty comes from roster construction and the native AI; human difficulty assessment requires playtesting.','Moves must occur in the runtime form’s `getAllLegalMoves()`, not just the global move list. Abilities must belong to the runtime form’s pool. Held item IDs must be registered. Registry validation supplements actual battles.']
for t in w['teams']:
 block=[f"## {name.get(t['id'],t['id'])}\n\n{t['strategy']}\n\n| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |", "|---|---|---|---|---|---|"]
 for m in t['members']:block.append(f"| {m['species']}{' ('+m['form']+')' if m.get('form') else ''} | {m['nature']} / {m['ability']} | `{m['item']}` | {m['evs']} | {m['ivOverrides'] or '6×31'} | {', '.join(m['moves'])} |")
 teams.append('\n'.join(block))
write('TRAINER_TEAMS.md','\n\n'.join(teams))
write('CHARACTER_ARCS.md','''# Character arcs

- Hale moves from parental concern and avoidance to naming his failure and keeping contact. His distraction appears in small practical details; serious admissions stay serious.
- Mara appears in the opening, unknown-contact aftermath, Selene’s correspondence, False Victory, final call and epilogue. Her humor is brief and situation-specific. Two side stories develop worry and postgame rematches.
- Orin connects independent records, admits that being right in an argument did not mean doing enough, and actually provides a short explanation when asked. His teaching and coffee disputes add relationships with Hale and Marlow.
- Rook explains Black Card as institutional access, smuggled Iris’s evidence for a fee and kept a useful copy. His morally flexible language does not absolve exploitation. Wallet, weather and later-letter stories confront the costs.
- Selene’s contradictory account is resolved through a changed shipment, not a random allegiance twist. Medical-privacy and rumor stories preserve her controlled skepticism.
- Iris is a coerced former League researcher who first falsified a report voluntarily. Rocket exploited that act. She preserves originals, returns to practical medical corrections and supplies a verifiable weather repair.
- Marlow prefers physical records, disagrees with Orin but needs both sources. Museum and photography stories show how institutions forget ordinary people.
- Aurelia is referenced before her battle and faces questions afterward. Secrecy was her decision; deletion was a later order she tolerated. Public-day content makes institutional accountability concrete.
- Vargan retains the originals and opposes the League’s silence. He grants access rather than every revelation. School trails, supplies, first aid and return-time rules give his institution a purpose beyond battles.
- ??? helps and manipulates, preserves the practice log and uses the player to reopen access. TOBA was rescued physically but erased administratively. The final explanation restores a person rather than introducing an unexplained magic identity. The postgame letter lets him communicate without another assignment.

Old personality counters remain in saves. Authored choices now record direct, careful, cautious and curious responses where relevant. Server-resolved answers update bounded counts. Conversations may converge mechanically while retaining different replies and a persisted transcript.
''')
write('SAVE_MIGRATION.md','''# Save migration and authored map points

`PlayerProgression.narrative` adds a null-safe schema, current main stage ID, finished stage IDs, chain cursors, completed chains, scene cursors, transcript, tendencies, loss counts, payment ledger and seasonal claims. Existing fields remain. Legacy contacts/messages retain their IDs; new phone follow-ups are appended.

The highest recognized authored victory milestone infers the preceding stages. Their flags and rematch eligibility are retained. Winning Vargan infers progress up to the School, not the new archive inspections. Existing fully completed saves retain completion and can enter postgame content. Missing fields normalize without replacing existing identities.

NPC positions remain in `npc_placements.json`. Inspection/travel points live separately in `narrative_points.json`; the map author places them with `/cworldpoi place <id>` while standing above the intended block. Default inspections use a lectern. The admin manifest is `world.json.pois`; ordinary players see translated place names and explicit objectives. No stock map coordinates are baked into quest prose.

The scammer uses a world-authored anchor and owner-bound runtime instances. Progression hides the actor after the first scene and permits its return for confrontation. Shared NPC recovery does not reconstruct it as a persistent mannequin. Saved crash residue fails the ordinary placement UUID check and is removed.

Seasonal entitlements preserve a reserved UUID before storage insertion, then a delivered state. Releasing or trading an obtained reward cannot reset its claim. BeastCoin rewards retain pending entitlements when BEconomy is absent; prepared but uncertain credits are held for reconciliation and logged, rather than automatically credited twice.

Test coverage and runtime limitations are recorded in the runtime QA report. Do not assume migration from an unknown production world was exercised merely because synthetic save tests pass.
''')
write('NARRATIVE_CHANGELOG.md','''# Narrative expansion

- Added seven Acts with 70 matching gameplay stages, with shopping moved into an optional Ren tutorial, including a full Rocket investigation, Town 8, False Victory, archive inspections, final explanation and epilogue.
- Added 43 substantial side chains and the 24-stage Weather Duo season.
- Added a dedicated first-party dialogue screen with coherent acceptance, question and refusal branches, server-held revisions, wrapped/scrollable text, response turns, a scrollable conversation journal and persistent cursors/history.
- Appended cast contacts and response-specific event-triggered phone follow-ups while retaining legacy message IDs. The first unknown message arrives after the opening investigation; the final call follows the archive and cast reactions.
- Added 30 competitive rosters with perfect-IV defaults, explicit exceptions, progressive EVs, legal form-aware moves, held items, native StrongBattleAI and player-relative levels.
- Added 29 distinct trainer win/loss voices, safe rematches and Mara’s postgame roster; removed generic outcome boilerplate and obsolete seal references.
- Added authored inspection points, owner-bound scam actor transitions, actual collection/capture/heal hooks and dual seasonal PC-safe rewards.
- Retained dedicated shops, dynamic Armory/Armors catalogs, BeastCoin transactions, phone apps, native faction/island war and existing anchored NPC presentation.

Runtime and visual claims belong to the accompanying QA report; content counts and a local build alone do not establish them.
''')
print('DOCUMENTED',len(w['campaign']),len(w['chains']),len(w['teams']))
