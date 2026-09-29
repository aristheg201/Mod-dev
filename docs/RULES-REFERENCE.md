# Rules reference and deliberate differences

Reviewed 2026-09-29 before implementation:

- Konami official rulebook landing page: https://www.yugioh-card.com/en/rulebook/
- Konami Fast Effect Timing: https://www.yugioh-card.com/eu/play/fast-effect-timing/
- Konami Damage Step Rules: https://www.yugioh-card.com/eu/play/damage-step-rules/

The current prototype borrows open game state, turn-player priority after non-chain actions, alternating response priority, two passes to resolve, reverse resolution, costs committed at activation, and separate effect/activation negation. This is an independent ruleset, not a claim of Yu-Gi-Oh rules compatibility.

The first player does not draw or attack on the first turn. Later turns draw once on entering Draw. Main Deck, Extra Deck, opening hand, Life, copies, zone counts, normal plays and type bonus come from the catalog. Phases advance only after the other player has an opportunity to respond. A response cancels a pending phase transition.

Normal plays, evolutions and Extra Deck evolutions currently open response windows after successful placement. Each attack opens an attack-declaration response window. A changed or missing target permits a new declaration. Battle power difference damages the losing Trainer; equal power destroys both Pokemon. Type advantage currently adds the configured bonus. This implementation does not yet include Yu-Gi-Oh's full Damage Step or summon-negation procedure.

Speed 1 effects require your open Main Phase. Speed 2 effects can respond to speed 1 or 2. Speed 3 effects require speed 3 responses. Thus the illustrative four-card chain in the product brief is only legal if the cards' configured response speeds permit it; names do not bypass speed rules.

Effects validate targets and costs before mutation. Negation does not refund costs. Card movement history retains owner, controller, old and new zone, cause, source and chain link. Public projections omit both decks, opposing hand contents and opposing Extra Deck contents. Spectator projections omit all hands and Extra Decks.

Pending rules work is explicitly tracked in implementation notes. These gaps must not be represented as full competitive rules coverage.
