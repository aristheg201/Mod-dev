# Duel Realm production bundle

This bundle is applied after the legacy Card Worlds source restore so the production build receives the verified Duel Realm implementation atomically.

Production gates covered by the bundle:

- dedicated `svarcade_tcg:duel_realm` void dimension;
- server-side coliseum structure with duel podiums and spectator galleries;
- full Yu-Gi-Oh-style board layout: Monster, Spell/Trap, Extra Monster, Field, Deck, Graveyard, Extra Deck and Banished positions;
- duel arena allocation, teleport and return-position restoration;
- spectator join/leave lifecycle with public-information-only views and no duel input authority;
- Spell/Trap set, face-down and timing rules;
- data-driven advanced summon profiles, including the Creation sequence;
- orbit free-look, wheel zoom and camera reset;
- runtime visual QA targeted at the duel realm rather than menu-only screenshots.
