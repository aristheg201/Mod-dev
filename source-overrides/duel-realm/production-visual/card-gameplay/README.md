Card gameplay identities

`pokemon_effects.json` is an explicit registry keyed by species plus exact aspects,
or by an existing custom card ID. The runtime performs a map lookup. Adding a
card never changes another card's gameplay. Catalog position is used only by the
existing presentation choreography, whose shapes and timing are preserved.

`designs.py` contains curated rules for the starter families, several contrasting
Water cards, signature legendaries, legacy extra-deck identities, and Unown glyphs.
Other individual kits are compiled from real species/form learnsets and the
bundled Showdown move metadata. These are TCG adaptations, not a claim that every
card was manually written or that the Pokémon battle damage formula is executed.
The frozen registry includes the selected moves for review. The authoring command
is `python3 card-gameplay/compile.py`; normal builds only read the frozen resource.
Source metadata comes from Cobblemon 1.8.1 (MPL-2.0) and the installed Mega Showdown
species resources; move metadata comes from its bundled Pokémon Showdown (MIT).

Primary mechanics must be distinct without text, VFX, counter/memory names,
numeric tuning, elemental/family filters, or operation ordering. Runtime validation
repeats this against the actual hydrated catalog; QA requires complete official
coverage and zero external fallbacks in the installed runtime. Unknown third-party species/forms retain their existing
integration/fallback until their provider supplies authored content; this pass does
not claim to author unknown third-party species.

Action tests execute all learnset-backed kits through normal authoritative commands,
and separately assert actual Blastoise, Onix, Venusaur, and Charmander results.
Monster LP taxes remain absent; Mind Theft's 800 LP signature remains valid.

A queued end-of-turn effect now compares its deadline with the event's captured
turn. The old engine compared it with the incremented current turn and could discard
it. This intentional correctness fix is applied to the frozen comparison engine too,
so deterministic optimization tests compare the same game rule in both engines.
