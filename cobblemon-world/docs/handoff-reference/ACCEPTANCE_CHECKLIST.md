# Acceptance checklist

Codex must not call the task complete until every applicable item below is demonstrated.

## Build/runtime

- [ ] Java 21 local build succeeds.
- [ ] remapped production JAR succeeds.
- [ ] Minecraft 1.21.1 client starts with target dependency set.
- [ ] server/integrated server starts without CobblemonWorld errors.
- [ ] no duplicate old CobblemonWorld JAR is present during QA.

## Phone/dialogue

- [ ] phone home opens.
- [ ] all required apps remain available.
- [ ] `???` conversation is a real alternating transcript.
- [ ] player replies show player name.
- [ ] 3+ response options can scroll/click.
- [ ] branch effects persist across reopen/restart.
- [ ] Vietnamese copy is natural and not mojibake.
- [ ] no raw objective ids/coordinates/commands in normal UI.

## Objective/navigation

- [ ] `Pin to Objective` immediately updates client state.
- [ ] compass/arrow HUD appears near level/XP area.
- [ ] target name appears.
- [ ] distance updates while moving.
- [ ] direction updates while turning.
- [ ] moving/re-placing target NPC updates target.
- [ ] reset command works.
- [ ] no silent renderer exception swallowing.

## NPCs

- [ ] placed/recovered NPC pitch is not downward.
- [ ] NPC faces player on interaction.
- [ ] anchored NPC has restrained idle look behavior.
- [ ] Ren opens real custom shop.
- [ ] Elle opens real custom shop.
- [ ] Tomo opens real bicycle shop/service.
- [ ] Mira performs real heal/daycare service.
- [ ] trainer NPC starts battle.
- [ ] quest NPC interaction can progress objective.
- [ ] `mysterious` is story-spawned only.
- [ ] `resonance_heart` is absent from active main-story progression.
- [ ] Town 8 qualifier progression exists before Battle Tower.

## Shop UI

- [ ] not clickable chat.
- [ ] not vanilla chest UI.
- [ ] custom shop screen visually follows the supplied fantasy/pixel reference direction.
- [ ] real ItemStack model/texture is rendered.
- [ ] selected/hover/disabled states are clear.
- [ ] BeastCoin balance is visible.
- [ ] category filter works.
- [ ] mouse-wheel/scrollbar works.
- [ ] 200+ Armory items remain usable.
- [ ] all actual registered Armory items for supplied version are reachable unless explicitly technical/blacklisted.

## Economy/purchase

- [ ] no default item > 500 BeastCoin.
- [ ] no 250k/absurd currency values.
- [ ] correct BeastCoin currency is used.
- [ ] server recomputes price and item from catalog entry.
- [ ] client cannot forge price.
- [ ] insufficient funds path works.
- [ ] exact funds path works.
- [ ] successful purchase grants correct count.
- [ ] UI balance refreshes immediately.
- [ ] inventory-full/grant-failure path refunds.
- [ ] rapid click cannot duplicate/overcharge.
- [ ] BEconomy missing -> fail closed with useful message/log.

## Final proof

- [ ] runtime QA report included.
- [ ] screenshots for phone, compass, dialogue, Ren shop, Elle shop, item detail, Tomo shop, NPC facing, Town 8 proof.
- [ ] final JAR SHA-256 included.
- [ ] full source ZIP SHA-256 included.
