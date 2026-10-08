# CobblemonWorld — consolidated implementation specification

## 1. Product intent

CobblemonWorld is a first-party RPG layer for Cobblemon. The smartphone, story progression, objectives, NPCs, faction system, services, shops, league flow, and navigation are part of **this mod**, not a loose set of integrations.

The player should experience a coherent RPG world, not a collection of NPCs that only print text. NPCs must start battles, heal, sell, unlock progression, progress quests, or otherwise perform an authored gameplay function.

Core systems must be server-authoritative and data/config-driven where practical. Client UI is presentation/input only.

## 2. Non-negotiable platform

- Minecraft 1.21.1
- Fabric
- Java 21
- Cobblemon 1.8.1+1.21.1
- Fabric Loader 0.18.4
- Fabric API 0.116.17+1.21.1
- Mod id `cobblemonworld`
- Canonical repo `aristheg201/Mod-dev`
- Canonical branch `feature/cobblemon-world-rpg-20261006`

Optional SVFrame/MMO hooks may remain graceful integrations, but the smartphone and faction systems must not depend on them to exist.

## 3. Known failures that this pass must eliminate

1. NPCs stand with their heads pitched down and feel like mannequins.
2. Pinned objectives do not reliably show a useful direction to the target.
3. Quest/story/dialogue copy is verbose AI-like filler, contains legacy jargon/IDs, and is not consistently Vietnamese.
4. Several NPCs only talk and do not provide their promised service.
5. No reliable objective-reset path in actual runtime.
6. User-visible command/literal/debug text leaks into gameplay.
7. Shop NPCs previously had no shop; later patches used clickable chat, which is explicitly rejected.
8. The first custom shop UI was visually poor and did not expose the full Cobblemon Armory catalog.
9. The shop economy was absurdly inflated (for example 250,000 BeastCoin) even though an ordinary quest reward tops out around 20 BeastCoin.
10. Previous direct patches passed structural checks without proving the actual runtime path. Final QA must exercise real interaction paths.

## 4. First-party Trainer Phone

The phone remains a first-party CobblemonWorld feature inspired by the old Cobblemon Smartphone concept but expanded for this RPG.

Required apps:

- Trainer Card
- Objective
- Current Story
- Side Quests
- Level Cap
- Badges
- Contacts
- Messages
- League
- Faction

Requirements:

- In-world handheld overlay style; do not use a generic vanilla chest screen for the phone.
- Notifications must use Minecraft/Cobblemon-style top-right tutorial toasts.
- Phone state comes from a server snapshot.
- Actions are sent to the server and validated server-side.
- The Faction app is backed by CobblemonWorld's native faction service.
- Preserve current working RPG/level-cap/faction systems while cleaning the UI/runtime path.

## 5. Dialogue and Messages

Messages must behave like actual conversations rather than a list of exposition entries.

Required behavior:

- NPC line -> player reply -> NPC reply -> further choices.
- Player reply is stored/displayed as a real transcript turn under the actual player name.
- `???` remains `???` until story identity rules reveal otherwise.
- Multiple response options are supported; when there are more options than fit, mouse-wheel scrolling works.
- Responses may route to different dialogue nodes and apply response-specific server-authoritative quest/flag effects.
- The UI refreshes immediately after a response; no close/reopen requirement.
- Read/unread state persists.
- Existing authored trainer skins remain enforced; do not silently fall back to Steve/Alex for authored NPCs.

Copy requirements:

- Vietnamese must be a first-class locale (`vi_vn`).
- Keep English locale as well.
- Use translation keys for player-visible static UI text.
- Dialogue should be short, natural, character-specific, and gameplay-directed.
- Do not expose implementation IDs such as `first_anomaly_site`, `seal_chess`, `buried_resonance`, registry IDs, or raw coordinates.
- Remove legacy visible wording around relays/seals/resonance when it conflicts with the newer Divinos / League / School of Wolf story framing.
- `First Signal` was already intended to become `First Challenge`: the visible objective should direct the player to the actual gameplay challenge (for example defeat Mara Voss), not "go to coordinates" or "check the relay".

## 6. Objective pinning and navigation HUD

`Track Quest` should be presented as **Pin to Objective** / Vietnamese equivalent.

A pinned objective must create a temporary navigation HUD near the existing XP/level area:

- directional arrow/compass relative to player yaw;
- target display name (usually NPC/POI name);
- distance in blocks/meters;
- automatically updates as the player turns/moves;
- hidden when no valid objective is pinned;
- handles another dimension gracefully (show a clear state instead of a nonsense arrow).

Architecture:

- Do not store only the rendered objective description as the authoritative target.
- Store a stable objective/quest ID plus target metadata.
- Resolve NPC positions from `NpcPlacementStore` / the real `/cworld npc place` data.
- No hardcoded quest coordinates in visible content.
- Pinning must immediately send a fresh snapshot/target payload to the client.
- Completing/progressing/resetting a quest must also resync the HUD.
- If the target NPC is moved/re-placed, navigation must use the new saved position.
- Renderer failures must log a clear one-time diagnostic instead of being swallowed silently.

Reset commands:

- `/cworld story objective reset`
- `/cworldresetobjective`

Reset should clear/reset the currently pinned objective state and return to the main/story objective as designed, without deleting unrelated completed story progress.

## 7. NPC physical behavior

Placed NPCs are map actors, so they should not wander away, but they also must not look dead.

Required:

- On placement, rotation, reload/recovery: pitch is normalized to `0` unless an authored pose explicitly says otherwise.
- `/cworld npc rotate <id>` records yaw; it must not preserve a downward head pitch.
- On player interaction, NPC turns head/body toward the interacting player.
- Add constrained idle look behavior while keeping the NPC anchored to its saved position. Do not simply enable uncontrolled pathfinding.
- After a short idle timeout, the NPC may return toward its authored yaw.
- NPC lookup and placement survive server restart.

Avoid a permanent `setNoAi(true)` mannequin result unless a separate tick/look controller supplies the intended visual behavior.

## 8. NPC gameplay function

Service NPCs must actually provide their named service:

- `pokemall_ren`: opens the custom PokéMall shop HUD.
- `fashion_elle`: opens the custom Fashion/Armory shop HUD containing the full Cobblemon Armory catalog.
- `bicycle_tomo`: opens a bicycle shop/service; current item target is `mapkit:bicycle` when available.
- `daycare_mira`: performs the intended party-heal/daycare service in gameplay, not just dialogue.
- trainer NPCs: start real Cobblemon battles where authored.
- side-quest giver/interactor NPCs: interaction can actually advance the relevant objective.

If a required optional dependency/item is missing, show a localized service-unavailable message and log a precise admin diagnostic; do not silently do nothing.

## 9. NPC/story progression order

See `NPC_PROGRESSION.md` for the exact list.

Critical rules:

- Do not group-spawn NPCs arbitrarily; map placement/progression follows story order.
- Town 8 is a real missing progression gap in the current content. It must be filled before Battle Tower access.
- `mysterious` (`???`) is story-spawned only; do not require manual placement.
- `resonance_heart` is legacy residue and must not be part of the active main-story roster.
- After Town 8, flow proceeds Battle Tower -> Royal League -> School of Wolf -> TOBA/final `???` encounter.

## 10. Custom shop — UI direction

Clickable chat is rejected. The shop must be a dedicated custom Minecraft `Screen`.

Use `reference/medieval-shop-ui-reference.png` for design language only; do not copy third-party art pixel-for-pixel.

Visual target:

- polished pixel-art/fantasy merchant UI compatible with Cobblemon/Minecraft;
- parchment/light panel surfaces, dark brown frame, red/gold accents;
- clear `SHOP MENU`/localized header;
- merchant identity panel;
- category tabs/icons;
- large item grid;
- selected-item detail panel;
- prominent BUY button;
- visible BeastCoin balance;
- hover/selected/disabled states;
- scrollbar / mouse-wheel support;
- scales cleanly to common GUI scales/resolutions.

Render catalog entries with real `ItemStack` rendering so Armory/Cobblemon resource-pack models and textures appear correctly. Do not paint fake item icons.

Do not implement the final shop by piggybacking a fake `PhoneSnapshot` marker. Add dedicated shop networking and a dedicated `ShopScreen`.

Suggested clean source architecture:

- `ShopDefinition` / `ShopRegistry`
- data files under `data/cobblemonworld/shops/`
- `ShopService` server-side purchase/catalog validation
- `ShopOpenPayload` S2C
- `ShopUpdatePayload` S2C (or one snapshot payload reused cleanly)
- `ShopBuyPayload` C2S
- `ShopScreen` client-side

The client sends an entry/catalog key and requested quantity only. The client never supplies the trusted price or item id.

## 11. Fashion Elle / Cobblemon Armory catalog

Dependency reference: `cobblemonarmory-1.5.4-fabric-1.21.1.jar`.

The internal audit produced 204 candidate catalog lines, but the production solution should **not hardcode "204" as the truth**. Enumerate the actual registered items in the `cobblemonarmory` namespace at runtime or generate the data file from the actual registry/build dependency.

Goal: all legitimate registered Armory items are reachable from the fashion shop, including trainer hats/accessories, armor sets, Eeveelution sets, shiny variants, weapons, shields/gloves, and crafting/material items.

Do not silently omit items because a manual hardcoded list went stale.

Recommended categories:

- All
- Accessories / Hats
- Armor
- Weapons / Tools
- Materials
- Shiny

Category assignment can come from data overrides/tags with id-pattern fallback. The catalog must remain scrollable and usable with 200+ entries.

## 12. PokéMall and Bicycle catalogs

PokéMall baseline bundles:

- 16x Poké Ball — 20 BeastCoin
- 8x Great Ball — 30 BeastCoin
- 8x Ultra Ball — 45 BeastCoin
- 8x Potion — 16 BeastCoin
- 6x Super Potion — 24 BeastCoin
- 4x Revive — 40 BeastCoin

These are a baseline and may be moved to JSON config, but keep the economy in the same order of magnitude.

Bicycle baseline:

- `mapkit:bicycle` — 500 BeastCoin maximum baseline price.

If the item/mod is absent, the entry must be disabled/hidden cleanly rather than granting an invalid item.

## 13. BeastCoin economy

This is non-negotiable:

- Typical quest reward maximum is about **20 BeastCoin**.
- **No default shop item may cost more than 500 BeastCoin.**
- Never show absurd values such as 250,000 BeastCoin for ordinary shop content.
- Do not accidentally use HunterCoin or some unrelated primary currency.

Baseline Armory tiers from the current balancing pass:

- common materials: 25 BC
- rarer materials: 40–75 BC
- trainer hats/accessories: 60–80 BC
- normal armor: ~90–130 BC per piece
- normal weapons/tools: ~180–260 BC
- ordinary shiny armor: ~180–260 BC per piece
- Rayquaza armor: ~250–320 BC per piece
- shiny Rayquaza armor: ~360–450 BC per piece
- Zacian Sword: 500 BC
- Bicycle: 500 BC

Move these prices into data/config so they can be tuned without bytecode changes.

## 14. BEconomy purchase semantics

Use the BEconomy API directly when available. Do not make player-permission-dependent commands (`tellraw`, `give`, `beco ...`) the primary purchase mechanism.

Required transaction flow:

1. server receives `ShopBuyPayload` with shop id + entry id + quantity;
2. server resolves the current canonical catalog entry;
3. validate shop access and item availability;
4. validate price from server config, never from client data;
5. validate BeastCoin balance;
6. validate inventory/grant feasibility;
7. debit atomically or with a safe compensating transaction;
8. grant item(s);
9. if grant fails after debit, refund exactly;
10. send localized result toast/status + refreshed balance/catalog state.

Protect against duplicate/rapid-click purchase races. The server is authoritative.

If BEconomy/BeastCoin cannot be resolved, fail closed with a clear localized UI state and server log. Do not silently switch currency.

## 15. Localization and text quality

- `vi_vn` is required and should be natural Vietnamese.
- retain `en_us`.
- no mojibake/encoding damage.
- user-facing static UI uses translation keys.
- no literal command strings in gameplay UI.
- no raw registry ids unless in a debug/admin-only screen.
- no visible coordinates for normal objectives.
- no long generic AI exposition.
- NPC dialogue should communicate character + immediate gameplay purpose.

## 16. Commands/admin UX

Keep/finish the following operational commands:

- `/cworld npc place <id>`
- `/cworld npc rotate <id>`
- `/cworld npc list`
- `/cworld story objective reset`
- `/cworldresetobjective`

Admin output may contain useful ids, but normal player UI should not display raw commands or internal identifiers.

## 17. First-party faction requirement

Faction is implemented in CobblemonWorld itself. Preserve/finish native create/invite/accept/leave/disband/member/role/island-war state and the Phone Faction app.

Do not downgrade it into "integration with another faction mod". Optional platform bridges may expose data but are not the source of truth for the faction system.

## 18. Persistence and compatibility

- Existing saves should migrate/read safely where feasible.
- Existing legacy message keys must remain readable.
- NPC placement persists.
- quest/dialogue/faction progression persists.
- data reload should invalidate relevant content caches safely.
- do not wipe player story progress because a display-text schema changes.

## 19. Clean-up requirement

The latest internal JAR contains emergency reflection/bytecode patches under `io.github.aristheg201.cobblemonworld.directfix`. These were useful for diagnosis but are **not** the desired production architecture.

Codex should port the behavior into clean normal source and then remove the need for:

- fake phone snapshot shop markers;
- reflection-based renderer interception;
- reflection-based BEconomy command fallbacks as primary path;
- bytecode patch transformers in production;
- swallowed exceptions.

Keep the good behavior, not the hack structure.

## 20. Build and QA strategy

Build locally first using Java 21 and the existing Gradle/loom caches. Avoid repeated GitHub Actions cycles for trivial edits.

A static class check is not enough. Required runtime proof includes:

- client starts;
- integrated/dedicated server starts;
- phone opens;
- dialogue branch can be clicked and persists;
- non-OP player clicks Ren -> custom PokéMall opens;
- non-OP player clicks Elle -> custom Fashion shop opens;
- non-OP player clicks Tomo -> custom bicycle shop/service opens;
- Fashion catalog exposes all actual registered Armory items from the supplied version;
- item cards render real item models/textures;
- price values are sane and never exceed 500 BC by default;
- insufficient funds path works;
- exact funds path works;
- successful purchase updates balance without closing/reopening;
- full inventory/grant failure refunds correctly;
- rapid double click does not dupe/overcharge;
- pin objective immediately displays navigation HUD;
- arrow direction changes with yaw;
- distance changes with movement;
- re-placing an NPC updates the target position;
- reset command works;
- NPCs no longer look permanently downward;
- service NPCs perform real functions;
- Town 8 gap is implemented;
- Vietnamese UI/dialogue renders correctly without mojibake;
- no raw player-facing commands/coordinates/legacy internal ids.

Capture visual screenshots of at least:

- Phone home
- Objective app + pinned navigation HUD
- Messages branching dialogue
- PokéMall shop
- Fashion shop showing multiple Armory categories
- selected Armory item detail + Buy button + BeastCoin balance
- Bicycle shop/service
- NPC facing player
- Town 8 qualifier/progression proof

## 21. Final deliverables

Codex should return:

1. production-remapped JAR;
2. full updated source ZIP;
3. concise changelog;
4. QA report with exact commands/tests/results;
5. screenshot/visual-QA ZIP;
6. SHA-256 for final JAR and source ZIP.

Do not report "pass" solely because classes compile or because strings exist inside a JAR. Runtime interaction is the acceptance bar.
