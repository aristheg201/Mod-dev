# Trainer teams and difficulty


30 authored rosters, including Mara’s six-member postgame upgrade. All permanent stats default to 31 IV. The only overrides are explicit zero Speed for slow Trick Room/Gyro Ball builds and zero Attack on specific special attackers. Early EV investment rises from 20–25% to 40%, 65%, 80%, then complete optimized spreads. Tower onward uses complete spreads.

Levels match the challenger’s strongest party member and preserve small authored roster gaps. No hidden damage or speed bonuses are added. NPC skill is 5, using Cobblemon’s StrongBattleAI. The difficulty comes from roster construction and the native AI; human difficulty assessment requires playtesting.

Moves must occur in the runtime form’s `getAllLegalMoves()`, not just the global move list. Abilities must belong to the runtime form’s pool. Held item IDs must be registered. Registry validation supplements actual battles.

## Mara Voss

Aggressive tempo: Intimidate, U-turn and priority.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| infernape | jolly / blaze | `cobblemon:life_orb` | [0, 60, 0, 0, 0, 60] | 6×31 | closecombat, flareblitz, uturn, machpunch |
| staraptor | jolly / intimidate | `cobblemon:choice_scarf` | [0, 60, 0, 0, 0, 60] | 6×31 | bravebird, closecombat, uturn, quickattack |
| roselia | bold / naturalcure | `cobblemon:eviolite` | [60, 0, 60, 0, 0, 0] | 6×31 | gigadrain, stunspore, leechseed, protect |

## Dr. Cael Orin

Status, recovery and Encore punish impatient attacks.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| alakazam | timid / magicguard | `cobblemon:focus_sash` | [0, 0, 0, 100, 0, 100] | 6×31 | psychic, focusblast, shadowball, encore |
| slowbro | bold / regenerator | `cobblemon:rocky_helmet` | [100, 0, 100, 0, 0, 0] | 6×31 | scald, slackoff, psychic, thunderwave |
| umbreon | calm / synchronize | `cobblemon:leftovers` | [100, 0, 0, 0, 100, 0] | 6×31 | foulplay, wish, protect, toxic |

## Rook

Taunt, Knock Off and pivots create dirty but legal openings.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| crobat | jolly / infiltrator | `cobblemon:black_sludge` | [0, 160, 0, 0, 0, 160] | 6×31 | bravebird, uturn, taunt, roost |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 160, 0, 0, 0, 160] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 160, 0, 0, 0, 160] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [160, 0, 160, 0, 0, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |

## Selene Kade

Balanced defensive core, speed control and a physical win condition.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 200, 0, 0, 0, 200] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| corviknight | impish / pressure | `cobblemon:leftovers` | [200, 0, 200, 0, 0, 0] | 6×31 | bravebird, roost, defog, uturn |
| clefable | bold / magicguard | `cobblemon:leftovers` | [200, 0, 200, 0, 0, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [200, 0, 200, 0, 0, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |

## Sixth Warden

Emergency endurance with hazards, recovery and anti-setup.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| hippowdon | impish / sandstream | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | earthquake, slackoff, stealthrock, whirlwind |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| slowbro | bold / regenerator | `cobblemon:rocky_helmet` | [252, 0, 252, 0, 4, 0] | 6×31 | scald, slackoff, psychic, thunderwave |
| volcarona | timid / flamebody | `cobblemon:heavy_duty_boots` | [0, 0, 4, 252, 0, 252] | 6×31 | fierydance, bugbuzz, quiverdance, gigadrain |
| lucario | jolly / innerfocus | `cobblemon:focus_sash` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, closecombat, bulletpunch, crunch |

## Rocket Grunt A

Scrappy disruption, poison and priority.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| crobat | jolly / infiltrator | `cobblemon:black_sludge` | [0, 200, 0, 0, 0, 200] | 6×31 | bravebird, uturn, taunt, roost |
| bisharp | adamant / defiant | `cobblemon:black_glasses` | [0, 200, 0, 0, 0, 200] | 6×31 | throatchop, ironhead, suckerpunch, swordsdance |
| gengar | timid / cursedbody | `cobblemon:life_orb` | [0, 0, 0, 200, 0, 200] | 6×31 | shadowball, sludgebomb, focusblast, taunt |

## Rocket Grunt B

Pivot pressure with hazard control.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 212, 0, 0, 0, 212] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [212, 0, 212, 0, 0, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 212, 0, 0, 0, 212] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |

## Rocket Grunt C

Bulky poison core supports an aggressive closer.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| toxapex | bold / regenerator | `cobblemon:black_sludge` | [252, 0, 252, 0, 4, 0] | 6×31 | chillingwater, recover, haze, toxicspikes |
| crobat | jolly / infiltrator | `cobblemon:black_sludge` | [0, 252, 0, 0, 4, 252] | 6×31 | bravebird, uturn, taunt, roost |
| gengar | timid / cursedbody | `cobblemon:life_orb` | [0, 0, 4, 252, 0, 252] | 6×31 | shadowball, sludgebomb, focusblast, taunt |
| bisharp | adamant / defiant | `cobblemon:black_glasses` | [0, 252, 0, 0, 4, 252] | 6×31 | throatchop, ironhead, suckerpunch, swordsdance |

## Rocket Admin Vex

Disruption into a dark-steel win condition.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |
| hydreigon | timid / levitate | `cobblemon:choice_scarf` | [0, 0, 4, 252, 0, 252] | 6×31 | darkpulse, dracometeor, flamethrower, uturn |
| bisharp | adamant / defiant | `cobblemon:black_glasses` | [0, 252, 0, 0, 4, 252] | 6×31 | throatchop, ironhead, suckerpunch, swordsdance |

## Seventh Warden

Habitat-defense core: recovery, hazards and phazing.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| hippowdon | impish / sandstream | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | earthquake, slackoff, stealthrock, whirlwind |
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |
| milotic | bold / marvelscale | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | scald, recover, icebeam, haze |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| dragonite | adamant / multiscale | `cobblemon:heavy_duty_boots` | [0, 252, 0, 0, 4, 252] | 6×31 | dragondance, extremespeed, earthquake, roost |

## Captain Dorian Pike

Rain doubles down on legal Swift Swim speed and Water pressure.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| pelipper | bold / drizzle | `cobblemon:damp_rock` | [252, 0, 252, 0, 4, 0] | 6×31 | hurricane, surf, uturn, roost |
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |
| kingdra | modest / swiftswim | `cobblemon:choice_specs` | [0, 0, 4, 252, 0, 252] | 6×31 | hydropump, dracometeor, icebeam, surf |
| barraskewda | adamant / swiftswim | `cobblemon:choice_band` | [0, 252, 0, 0, 4, 252] | 6×31 | liquidation, closecombat, flipturn, psychicfangs |
| swampert | impish / torrent | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | earthquake, waterfall, stealthrock, roar |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |

## Tower Ace Rowan

Competitive tempo with hazard opening, pivots and Dragon Dance.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| alakazam | timid / magicguard | `cobblemon:focus_sash` | [0, 0, 4, 252, 0, 252] | 6×31 | psychic, focusblast, shadowball, encore |
| dragonite | adamant / multiscale | `cobblemon:heavy_duty_boots` | [0, 252, 0, 0, 4, 252] | 6×31 | dragondance, extremespeed, earthquake, roost |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |

## Tower Ace Nyx

Trick Room: intentional zero Speed, bulky setters and slow wallbreakers.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| hatterene | quiet / magicbounce | `cobblemon:life_orb` | [252, 0, 4, 252, 0, 0] | {'spe': 0, 'atk': 0} | trickroom, psychic, dazzlinggleam, mysticalfire |
| porygon2 | sassy / trace | `cobblemon:eviolite` | [252, 0, 4, 0, 252, 0] | {'spe': 0, 'atk': 0} | trickroom, recover, icebeam, thunderbolt |
| conkeldurr | brave / guts | `cobblemon:flame_orb` | [252, 252, 0, 0, 4, 0] | {'spe': 0} | drainpunch, facade, knockoff, machpunch |
| reuniclus | quiet / magicguard | `cobblemon:life_orb` | [252, 0, 4, 252, 0, 0] | {'spe': 0, 'atk': 0} | psychic, focusblast, recover, trickroom |
| torkoal | quiet / drought | `cobblemon:charcoal_stick` | [252, 0, 4, 252, 0, 0] | {'spe': 0} | eruption, lavaplume, earthpower, rapidspin |
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |

## Tower Ace Orion

Hazard balance: reliable defensive core and two finishing routes.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |
| toxapex | bold / regenerator | `cobblemon:black_sludge` | [252, 0, 252, 0, 4, 0] | 6×31 | chillingwater, recover, haze, toxicspikes |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| clefable | bold / magicguard | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |
| volcarona | timid / flamebody | `cobblemon:heavy_duty_boots` | [0, 0, 4, 252, 0, 252] | 6×31 | fierydance, bugbuzz, quiverdance, gigadrain |
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |

## League Elite Cassian

Sand history: weather turns, hazard stacking and Sand Rush.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| tyranitar | careful / sandstream | `cobblemon:smooth_rock` | [252, 0, 4, 0, 252, 0] | 6×31 | stoneedge, crunch, stealthrock, thunderwave |
| excadrill | jolly / sandrush | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, ironhead, rapidspin, swordsdance |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| clefable | bold / magicguard | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |

## League Elite Seraph

Institutional pressure: dual screens, setup and priority.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| grimmsnarl | careful / prankster | `cobblemon:light_clay` | [252, 0, 4, 0, 252, 0] | 6×31 | reflect, lightscreen, spiritbreak, taunt |
| dragonite | adamant / multiscale | `cobblemon:heavy_duty_boots` | [0, 252, 0, 0, 4, 252] | 6×31 | dragondance, extremespeed, earthquake, roost |
| azumarill | adamant / hugepower | `cobblemon:sitrus_berry` | [252, 252, 0, 0, 4, 0] | 6×31 | aquajet, playrough, liquidation, bellydrum |
| mimikyu | jolly / disguise | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, playrough, shadowclaw, shadowsneak |
| volcarona | timid / flamebody | `cobblemon:heavy_duty_boots` | [0, 0, 4, 252, 0, 252] | 6×31 | fierydance, bugbuzz, quiverdance, gigadrain |
| lucario | jolly / innerfocus | `cobblemon:focus_sash` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, closecombat, bulletpunch, crunch |

## League Elite Kael

Incident survivor: pivot offense with covered physical and special threats.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| hydreigon | timid / levitate | `cobblemon:choice_scarf` | [0, 0, 4, 252, 0, 252] | 6×31 | darkpulse, dracometeor, flamethrower, uturn |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |
| gengar | timid / cursedbody | `cobblemon:life_orb` | [0, 0, 4, 252, 0, 252] | 6×31 | shadowball, sludgebomb, focusblast, taunt |

## Champion Aurelia

Champion balance: hazards, removal, recovery, speed control and flexible endgames.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| milotic | bold / marvelscale | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | scald, recover, icebeam, haze |
| clefable | bold / magicguard | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |
| hydreigon | timid / levitate | `cobblemon:choice_scarf` | [0, 0, 4, 252, 0, 252] | 6×31 | darkpulse, dracometeor, flamethrower, uturn |
| metagross | adamant / clearbody | `cobblemon:assault_vest` | [252, 252, 0, 0, 4, 0] | 6×31 | meteormash, zenheadbutt, bulletpunch, earthquake |

## Wolf Trialist Fen

Discipline: sustain, removal and measured offense.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| hitmontop | impish / intimidate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | closecombat, rapidspin, machpunch, stoneedge |
| slowbro | bold / regenerator | `cobblemon:rocky_helmet` | [252, 0, 252, 0, 4, 0] | 6×31 | scald, slackoff, psychic, thunderwave |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| clefable | bold / magicguard | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |

## Wolf Trialist Skoll

Tracking: U-turn/Volt Switch, priority and lead disruption.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| crobat | jolly / infiltrator | `cobblemon:black_sludge` | [0, 252, 0, 0, 4, 252] | 6×31 | bravebird, uturn, taunt, roost |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |
| infernape | jolly / blaze | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | closecombat, flareblitz, uturn, machpunch |

## Wolf Trialist Hati

Rescue tempo: speed control and priority deny setup.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| talonflame | jolly / galewings | `cobblemon:heavy_duty_boots` | [0, 252, 0, 0, 4, 252] | 6×31 | bravebird, flareblitz, roost, uturn |
| lucario | jolly / innerfocus | `cobblemon:focus_sash` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, closecombat, bulletpunch, crunch |
| azumarill | adamant / hugepower | `cobblemon:sitrus_berry` | [252, 252, 0, 0, 4, 0] | 6×31 | aquajet, playrough, liquidation, bellydrum |
| mimikyu | jolly / disguise | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, playrough, shadowclaw, shadowsneak |
| hydreigon | timid / levitate | `cobblemon:choice_scarf` | [0, 0, 4, 252, 0, 252] | 6×31 | darkpulse, dracometeor, flamethrower, uturn |

## Master Vargan

Veteran balance: hazards, removal, defensive pivots and multiple win conditions.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| clefable | bold / magicguard | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | moonblast, moonlight, calmmind, thunderwave |
| volcarona | timid / flamebody | `cobblemon:heavy_duty_boots` | [0, 0, 4, 252, 0, 252] | 6×31 | fierydance, bugbuzz, quiverdance, gigadrain |
| metagross | adamant / clearbody | `cobblemon:assault_vest` | [252, 252, 0, 0, 4, 0] | 6×31 | meteormash, zenheadbutt, bulletpunch, earthquake |

## ???

Final surprise: Magic Bounce/Trick Room route with bulky slow attackers and priority.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| hatterene | quiet / magicbounce | `cobblemon:life_orb` | [252, 0, 4, 252, 0, 0] | {'spe': 0, 'atk': 0} | trickroom, psychic, dazzlinggleam, mysticalfire |
| porygon2 | sassy / trace | `cobblemon:eviolite` | [252, 0, 4, 0, 252, 0] | {'spe': 0, 'atk': 0} | trickroom, recover, icebeam, thunderbolt |
| conkeldurr | brave / guts | `cobblemon:flame_orb` | [252, 252, 0, 0, 4, 0] | {'spe': 0} | drainpunch, facade, knockoff, machpunch |
| reuniclus | quiet / magicguard | `cobblemon:life_orb` | [252, 0, 4, 252, 0, 0] | {'spe': 0, 'atk': 0} | psychic, focusblast, recover, trickroom |
| metagross | adamant / clearbody | `cobblemon:assault_vest` | [252, 252, 0, 0, 4, 0] | 6×31 | meteormash, zenheadbutt, bulletpunch, earthquake |
| mimikyu | jolly / disguise | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | swordsdance, playrough, shadowclaw, shadowsneak |

## Bảo

Coaching: partial investment, status awareness and Intimidate.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| eevee | jolly / adaptability | `cobblemon:eviolite` | [0, 48, 0, 0, 0, 48] | 6×31 | quickattack, bite, babydolleyes, protect |
| growlithe | jolly / intimidate | `cobblemon:eviolite` | [0, 48, 0, 0, 0, 48] | 6×31 | flamewheel, bite, roar, willowisp |
| roselia | bold / naturalcure | `cobblemon:eviolite` | [48, 0, 48, 0, 0, 0] | 6×31 | gigadrain, stunspore, leechseed, protect |

## Minh

Demonstration: special pivot pressure with a defensive grass answer.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| pikachu | timid / static | `cobblemon:light_ball` | [0, 0, 0, 60, 0, 60] | 6×31 | thunderbolt, voltswitch, grassknot, nuzzle |
| roselia | bold / naturalcure | `cobblemon:eviolite` | [60, 0, 60, 0, 0, 0] | 6×31 | gigadrain, stunspore, leechseed, protect |
| growlithe | jolly / intimidate | `cobblemon:eviolite` | [0, 60, 0, 0, 0, 60] | 6×31 | flamewheel, bite, roar, willowisp |

## Đạt “Uy Tín”

Scammer: disruption is legal; currency risk remains only ten BeastCoin.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| crobat | jolly / infiltrator | `cobblemon:black_sludge` | [0, 188, 0, 0, 0, 188] | 6×31 | bravebird, uturn, taunt, roost |
| bisharp | adamant / defiant | `cobblemon:black_glasses` | [0, 188, 0, 0, 0, 188] | 6×31 | throatchop, ironhead, suckerpunch, swordsdance |
| gengar | timid / cursedbody | `cobblemon:life_orb` | [0, 0, 0, 188, 0, 188] | 6×31 | shadowball, sludgebomb, focusblast, taunt |

## Hải

Harbor Breathing: Shell Smash is the win condition, supported by screens.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| grimmsnarl | careful / prankster | `cobblemon:light_clay` | [252, 0, 4, 0, 252, 0] | 6×31 | reflect, lightscreen, spiritbreak, taunt |
| cloyster | jolly / skilllink | `cobblemon:white_herb` | [0, 252, 0, 0, 4, 252] | 6×31 | shellsmash, iciclespear, rockblast, iceshard |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |

## mara_voss_postgame

Postgame rival: full investment, six-member pivot offense and priority finishers.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| infernape | jolly / blaze | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | closecombat, flareblitz, uturn, machpunch |
| staraptor | jolly / intimidate | `cobblemon:choice_scarf` | [0, 252, 0, 0, 4, 252] | 6×31 | bravebird, closecombat, uturn, quickattack |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
| rotom (wash) | bold / levitate | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | hydropump, voltswitch, willowisp, painsplit |
| weavile | jolly / pressure | `cobblemon:life_orb` | [0, 252, 0, 0, 4, 252] | 6×31 | knockoff, iciclespear, iceshard, swordsdance |

## Vệ binh Địa tầng Khoa

Land guardian: sun plus hazards, with recovery and legal Chlorophyll.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| torkoal | quiet / drought | `cobblemon:charcoal_stick` | [252, 0, 4, 252, 0, 0] | {'spe': 0} | eruption, lavaplume, earthpower, rapidspin |
| venusaur | modest / chlorophyll | `cobblemon:life_orb` | [0, 0, 4, 252, 0, 252] | 6×31 | gigadrain, sludgebomb, growth, sleeppowder |
| garchomp | jolly / roughskin | `cobblemon:rocky_helmet` | [0, 252, 0, 0, 4, 252] | 6×31 | earthquake, dragonclaw, stealthrock, swordsdance |
| volcarona | timid / flamebody | `cobblemon:heavy_duty_boots` | [0, 0, 4, 252, 0, 252] | 6×31 | fierydance, bugbuzz, quiverdance, gigadrain |
| corviknight | impish / pressure | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | bravebird, roost, defog, uturn |
| conkeldurr | brave / guts | `cobblemon:flame_orb` | [252, 252, 0, 0, 4, 0] | {'spe': 0} | drainpunch, facade, knockoff, machpunch |

## Vệ binh Hải triều Nhi

Sea guardian: rain, Swift Swim, pivots and a resilient water core.

| Pokémon | Nature / ability | Held item | EVs HP/Atk/Def/SpA/SpD/Spe | IV overrides | Moves |
|---|---|---|---|---|---|
| pelipper | bold / drizzle | `cobblemon:damp_rock` | [252, 0, 252, 0, 4, 0] | 6×31 | hurricane, surf, uturn, roost |
| kingdra | modest / swiftswim | `cobblemon:choice_specs` | [0, 0, 4, 252, 0, 252] | 6×31 | hydropump, dracometeor, icebeam, surf |
| barraskewda | adamant / swiftswim | `cobblemon:choice_band` | [0, 252, 0, 0, 4, 252] | 6×31 | liquidation, closecombat, flipturn, psychicfangs |
| ferrothorn | relaxed / ironbarbs | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | {'spe': 0} | gyroball, leechseed, spikes, powerwhip |
| milotic | bold / marvelscale | `cobblemon:leftovers` | [252, 0, 252, 0, 4, 0] | 6×31 | scald, recover, icebeam, haze |
| scizor | adamant / technician | `cobblemon:leftovers` | [0, 252, 0, 0, 4, 252] | 6×31 | bulletpunch, uturn, swordsdance, roost |
