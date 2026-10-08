# Weather Duo: season weather_duo_01

Prerequisite: completed campaign. Giver: Tiến sĩ Mai. 24 stages across forest stations, archives, harbor, geological chamber and sea shrine.

The false alerts originate in two separated modules of a Pokémon-refuge monitoring system. Groudon and Kyogre answer incompatible rescue calls. The story repairs the source of those calls and retains evidence of the removal, rather than recreating a land-versus-sea war.

| # | Stable stage ID | Mechanic | Target | Objective |
|---|---|---|---|---|
| 1 | `weather_duo_01.1` | talk | Tiến sĩ Mai | Take the postgame weather report. |
| 2 | `weather_duo_01.2` | talk | Thảo | Ask Thao how the sea changed. |
| 3 | `weather_duo_01.3` | inspect | Tide gauge | Inspect the harbor tide gauge. |
| 4 | `weather_duo_01.4` | talk | Hạnh | Ask Hanh about the dry river and displaced Pokémon. |
| 5 | `weather_duo_01.5` | deliver | Hạnh | Deliver 4 iron ingots for the station’s water storage. |
| 6 | `weather_duo_01.6` | inspect | Dry river survey | Inspect the dry-river water marker. |
| 7 | `weather_duo_01.7` | talk | Dr. Cael Orin | Ask Orin to compare the cycles. |
| 8 | `weather_duo_01.8` | talk | Dr. Gideon Marlow | Ask Marlow about the weather regulator. |
| 9 | `weather_duo_01.9` | inspect | Ancient geological tablet | Read the cavern’s geological tablet. |
| 10 | `weather_duo_01.10` | talk | Rook | Ask Rook who bought the regulator module. |
| 11 | `weather_duo_01.11` | talk | Tiến sĩ Mai | Ask Mai how the old module was used. |
| 12 | `weather_duo_01.12` | inspect | Groudon’s geological chamber | Inspect Groudon’s geological chamber; this is not a claim. |
| 13 | `weather_duo_01.13` | talk | Hoa | Ask Hoa for the sea shrine route. |
| 14 | `weather_duo_01.14` | inspect | Ancient tidal tablet | Inspect the sea shrine tidal reference. |
| 15 | `weather_duo_01.15` | talk | Vệ binh Hải triều Nhi | Hear Nhi’s first Kyogre sighting report. |
| 16 | `weather_duo_01.16` | inspect | Kyogre’s tidal chamber | Inspect Kyogre’s tidal chamber; this is not a claim. |
| 17 | `weather_duo_01.17` | talk | Dr. Iris Vale | Ask Iris how to stop the looping signal. |
| 18 | `weather_duo_01.18` | deliver | Dr. Iris Vale | Deliver 2 redstone for Iris’s acknowledgment circuit. |
| 19 | `weather_duo_01.19` | battle | Vệ binh Địa tầng Khoa | Defeat Khoa, guardian of the geological chamber. |
| 20 | `weather_duo_01.20` | inspect | Central regulator | Inspect the reconnected central regulator and its acknowledgment. |
| 21 | `weather_duo_01.21` | battle | Vệ binh Hải triều Nhi | Defeat Nhi, guardian of the tidal chamber. |
| 22 | `weather_duo_01.22` | claim | Vệ binh Địa tầng Khoa | Claim Groudon after restoring balance and passing both guardians. |
| 23 | `weather_duo_01.23` | claim | Vệ binh Hải triều Nhi | Claim Kyogre; Groudon does not lock this reward. |
| 24 | `weather_duo_01.24` | talk | Tiến sĩ Mai | Finish Mai’s report and hear the cast’s reactions. |

The first two sightings use actual Cobblemon Pokémon models. Sightings cannot be captured or challenged, expire, and are removed on shutdown. Final acquisition is a guaranteed, visible partnership reward after both guardian battles and restoring balance. It uses Cobblemon storage directly, with separate one-time entitlements for both species. It is not a wild capture reward.

Reward policy: level 70; non-shiny; Adamant/Drought Groudon or Modest/Drizzle Kyogre; six IVs of 25; four authored moves. A full party sends the reward to PC. A reserved UUID is persisted before grant; a delivered entitlement never recreates a released or traded Pokémon. Unavailable/full PC leaves the reservation retryable. There is no cross-database atomic transaction between Cobblemon storage and the progression JSON: a process crash at that boundary requires reconciliation rather than an automatic duplicate grant.

The `seasons` and `rewards` records in `world.json` support future season IDs, prerequisites, chains, Pokémon properties, IV policy and guardian requirements. Reward IDs are unique across the loaded definitions; old entitlements remain in saves when a season definition changes.
