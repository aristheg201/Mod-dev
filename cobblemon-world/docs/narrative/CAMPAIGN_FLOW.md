# Campaign flow


Seven Acts group 70 stages into chapters. Stages advance only from their own matching conversation, completed battle, inspected authored point, service, inventory delivery or purchase.

New saves begin with Hale. Legacy flags infer completed preceding stages without discarding contacts, messages, quests, placements or faction data.


## Act 0

A number to call → Before the road → First-day supplies → The forgotten basket → Science needs lunch → A rival on the same road → The First Challenge → The missing second → What he will not say → A message from nobody → Do not go alone

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `hale_phone` | talk | Professor Elias Hale | Talk to Hale about the journey and your Trainer Phone. |
| 2 | `mira_visit` | heal | Mira - Daycare | Ask Mira to check and heal your Pokémon party. |
| 3 | `ren_supplies` | buy | Ren - PokeMall | Buy a supply bundle from Ren’s shop. |
| 4 | `lan_errand` | talk | Cô Lan | Ask Lan what Hale forgot. |
| 5 | `hale_lunch` | deliver | Professor Elias Hale | Deliver 3 apples to Hale. |
| 6 | `mara_intro` | talk | Mara Voss | Meet Mara at the practice field. |
| 7 | `mara_first` | battle | Mara Voss | Win your first proper challenge against Mara. |
| 8 | `field_fault` | inspect | Practice log | Inspect the practice-field log after the battle. |
| 9 | `hale_fault` | talk | Professor Elias Hale | Tell Hale what the practice log showed. |
| 10 | `unknown_first` | talk | Bác Phúc | Ask Phuc about the abandoned practice-field mailbox. |
| 11 | `mara_aftermath` | talk | Mara Voss | Show Mara the strange message before leaving for town two. |


## Act I

Town of records → The shared date → A test with an answer → The short explanation → Courier street → One card, two purposes → A civil exchange → The cost of entry → The closed garden → The right signature, the wrong cargo → Do not judge by a glance → An unofficial request → The response station → The unmanned shift → Permission to enter danger → A limited warrant

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `orin_arrival` | talk | An | Ask An about the records Orin requested. |
| 2 | `orin_records` | inspect | Library lending register | Inspect the duplicate record at the library reading desk. |
| 3 | `orin_challenge` | battle | Dr. Cael Orin | Defeat Orin’s control team. |
| 4 | `orin_summary` | talk | Dr. Cael Orin | Ask Orin for the concise record analysis and a lead to Rook. |
| 5 | `rook_contact` | talk | Nam | Ask Nam about deliveries missing from the ledger. |
| 6 | `black_invoice` | inspect | Black Card invoice | Inspect the Black Card invoice in the delivery depot. |
| 7 | `rook_battle` | battle | Rook | Defeat Rook for the Black Card and shipping ledger. |
| 8 | `rook_warning` | talk | Rook | Ask Rook who bought depot access and why Selene is involved. |
| 9 | `selene_local` | talk | Yến | Ask Yen why the protected garden is closed. |
| 10 | `selene_log` | inspect | Garden gate register | Read the protected garden gate log. |
| 11 | `selene_battle` | battle | Selene Kade | Defeat Selene’s balanced team. |
| 12 | `selene_aftermath` | talk | Selene Kade | Take Selene’s referral to the Sixth Warden. |
| 13 | `warden_ranger` | talk | Hạnh | Ask Hanh about the Pokémon incidents near the old depot. |
| 14 | `warden_log` | inspect | Rescue duty board | Inspect the emergency station duty board. |
| 15 | `warden_six` | battle | Sixth Warden | Pass the Sixth Warden’s emergency-response battle. |
| 16 | `warden_orders` | talk | Sixth Warden | Receive the Sixth Warden’s Rocket surveillance instructions. |


## Act II

The night shift → Below the warehouse → Procedure says stop → The shift recording → The password keeper → The unmapped door → A name for sale → The scientist who signed → Keep the original

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `rocket_informant` | talk | Đức | Ask Duc how to enter the Rocket warehouse. |
| 2 | `rocket_route` | inspect | Warehouse delivery board | Inspect the delivery board for the basement route. |
| 3 | `rocket_guard1` | battle | Rocket Grunt A | Defeat the first Rocket checkpoint guard. |
| 4 | `rocket_overhear` | inspect | Rocket shift desk | Inspect the shift recording in the basement corridor. |
| 5 | `rocket_guard2` | battle | Rocket Grunt B | Defeat the data-room guard. |
| 6 | `rocket_guard3` | battle | Rocket Grunt C | Defeat the laboratory access guard. |
| 7 | `rocket_vex` | battle | Rocket Admin Vex | Defeat Vex and open Iris’s room. |
| 8 | `iris_rescue` | talk | Dr. Iris Vale | Speak to Iris and decide how to preserve the evidence. |
| 9 | `iris_terminal` | inspect | Iris’s archive terminal | Inspect Iris’s reader and preserve the original record. |


## Act III

The missing person in the photo → The impression on paper → Keeping the site safe → The harbor shift → The passenger who never boarded → The harbor final qualifier → Finished. Not yet.

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `marlow_account` | talk | Dr. Gideon Marlow | Ask Marlow to compare the TOBA record. |
| 2 | `marlow_photo` | inspect | Old town photograph | Inspect town seven’s old photograph board. |
| 3 | `warden_seven` | battle | Seventh Warden | Pass the Seventh Warden’s safety challenge. |
| 4 | `liora_harbour` | talk | Harbour Marshal Liora | Meet Liora at town eight’s harbor. |
| 5 | `harbour_manifest` | inspect | Harbor rescue manifest | Read the rescue ship manifest. |
| 6 | `dorian_qualifier` | battle | Captain Dorian Pike | Defeat Dorian to complete the eight-town circuit. |
| 7 | `false_victory` | talk | Mara Voss | Celebrate with Mara, then compare the completed circuit record. |


## Act IV

The winners’ waiting room → Rowan: the opening tempo → Nyx: waiting for the right moment → Orion: the final record → A conditional invitation → Cassian: keeper of history → Seraph: signatures are not innocent → Kael: the one who stayed → Aurelia: a battle and a responsibility

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `tower_entry` | talk | Mina - Battle Tower | Register with the Battle Tower receptionist. |
| 2 | `tower_rowan` | battle | Tower Ace Rowan | Defeat Tower Ace Rowan in the Battle Tower. |
| 3 | `tower_nyx` | battle | Tower Ace Nyx | Defeat Tower Ace Nyx in the Battle Tower. |
| 4 | `tower_orion` | battle | Tower Ace Orion | Defeat Tower Ace Orion in the Battle Tower. |
| 5 | `league_entry` | talk | Royal League Steward | Register for the Royal League after qualifying at the Tower. |
| 6 | `elite_cassian` | battle | League Elite Cassian | Defeat League Elite Cassian in the Royal League. |
| 7 | `elite_seraph` | battle | League Elite Seraph | Defeat League Elite Seraph in the Royal League. |
| 8 | `elite_kael` | battle | League Elite Kael | Defeat League Elite Kael in the Royal League. |
| 9 | `champion_aurelia` | battle | Champion Aurelia | Defeat Champion Aurelia in the Royal League. |


## Act V

What the Champion owes → A gate fame cannot open → Fen: keep the team standing → The rescue trail → Skoll: read the opponent → Bring enough for someone else → Hati: a battle that must end → Vargan: the right to read

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `aurelia_aftermath` | talk | Champion Aurelia | Hear Aurelia’s account and her referral to the School of Wolf. |
| 2 | `wolf_bran` | talk | Bran - Wolf Gatekeeper | Ask Bran about the School’s three trials. |
| 3 | `wolf_fen` | battle | Wolf Trialist Fen | Pass Fen’s combat-discipline battle. |
| 4 | `wolf_track` | inspect | Rescue trail marker | Inspect Skoll’s rescue-trail marker. |
| 5 | `wolf_skoll` | battle | Wolf Trialist Skoll | Defeat Skoll after inspecting the trail. |
| 6 | `wolf_supply` | deliver | Wolf Trialist Hati | Deliver 4 potatoes as rescue supplies to Hati. |
| 7 | `wolf_hati` | battle | Wolf Trialist Hati | Defeat Hati after preparing rescue supplies. |
| 8 | `wolf_vargan` | battle | Master Vargan | Defeat Vargan to unlock archive access. |


## Act VI

Record one: the volunteer → Record two: two times → Record three: the altered order → The promise not kept → The final call → The last person → More than a record → A name restored → Come home and sleep → Rest today

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `archive_record1` | inspect | Record one: the volunteer | Inspect record one: the volunteer in the archive. |
| 2 | `archive_record2` | inspect | Record two: two times | Inspect record two: two times in the archive. |
| 3 | `archive_record3` | inspect | Record three: the altered order | Inspect record three: the altered order in the archive. |
| 4 | `archive_hale` | talk | Professor Elias Hale | Ask Hale about TOBA’s rescue record. |
| 5 | `final_unknown` | talk | Quản thủ Ánh | Receive the unknown call at the archive desk. |
| 6 | `final_confrontation` | battle | ??? | Meet and challenge the unknown sender. |
| 7 | `final_explanation` | talk | Quản thủ Ánh | Hear TOBA’s explanation at the archive after the battle. |
| 8 | `final_choice` | talk | Champion Aurelia | Choose to publish the record with Aurelia and protect those affected. |
| 9 | `epilogue_hale` | talk | Professor Elias Hale | Visit Hale after restoring the records. |
| 10 | `epilogue_mara` | talk | Mara Voss | Meet Mara to close the campaign and unlock postgame adventures. |


The eight-town qualifier precedes False Victory and the Tower. Tower registration precedes Rowan, Nyx and Orion. League registration precedes Cassian, Seraph, Kael and Aurelia. Aurelia’s aftermath precedes Bran and the School. Vargan grants archive access; all three records, Hale’s account and the final call remain separate objectives. Winning the final battle opens the explanation and restoration scenes; the epilogue unlocks postgame.
