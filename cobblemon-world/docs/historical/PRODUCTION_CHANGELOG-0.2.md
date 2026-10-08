# CobblemonWorld production pass — 2026-10-08

- Replaced diagnostic shop hooks with dedicated server-authoritative shops, networking, transactions, and an original wood-and-parchment shop screen.
- Added registry-generated fashion inventory for both Cobblemon Armory and Cobblemon Armors, categories, scrolling, selected-item details, and real ItemStack models that respect resource packs.
- Rebalanced ordinary BeastCoin shops to 1–500 BC, with low-cost PokéMall bundles, explicit BeastCoin account selection, inventory checks, rapid-click protection, grant rollback, and exact refunds.
- Made Ren, Elle, Tomo, and Mira provide their actual shop or healing service. Missing optional items remain visibly unavailable.
- Anchored placed NPCs, normalized pitch, persisted authored facing, and added restrained idle looks and interaction facing without wandering.
- Added stable pinned objective identity and immediate navigation updates, live direction/distance, placement-store targets, unavailable/dimension states, and both objective reset commands.
- Preserved branching conversations and readable legacy messages, with named player replies, response-specific effects, transcript persistence, and immediate phone refresh.
- Rewrote English and Vietnamese player text around actual gameplay, localized shop/faction/navigation UI, and preserved the unknown speaker until the story reveal.
- Added Town 8's Harbour Marshal Liora and Captain Dorian qualifier before Battle Tower, enforced the Tower/League/Wolf order, and made the final actor story-spawned. Removed the obsolete resonance heart from active progression while retaining compatibility data.
- Scaled trainer teams to the strongest current party Pokémon at battle start, preserving authored level gaps. Server owners can configure the offset or disable scaling.
- Preserved the first-party Trainer Phone, faction, island-war, progression, level-cap, and optional integration systems; added guarded legacy progression/objective migration.
- Added a Java 21/Gradle 8.12 build wrapper, data validation, transaction/scaling tests, and opt-in isolated production runtime QA with real client screenshots.

See the runtime report for exercised paths and explicitly unproven coverage. Test drivers are disabled unless their QA JVM properties are deliberately enabled.
