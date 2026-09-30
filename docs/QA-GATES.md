# Card Worlds acceptance gates

A change is not considered complete because a mockup exists or because Java compiles.

## Mandatory automated gate

- Java 21 / Minecraft 1.21.1 / Fabric Loader 0.18.4 / Fabric API 0.116.6 / Loom 1.7.4.
- `gradle clean verifyCardWorlds` must pass.
- Engine and economy JUnit suites must report zero failures/errors.
- Production and QA-driver remapped jars must both be produced.
- `fabric.mod.json` must be readable from the production jar and identify `svarcade_tcg`.

## Mandatory visual gate before release

Run `tools/launch_visual_qa.py` on the target client installation and inspect every captured screen. A release is blocked by any of the following:

- blank Pokemon artwork/model viewport;
- placeholder/random artwork presented as finished card art;
- missing pack entry/open/flip animation;
- corrupted font/glyph output;
- clipped navigation, buttons, cards or duel zones at GUI scale 2 or 3;
- keybind stealing focus from chat/text fields;
- a screen that differs materially from the accepted Card Worlds visual language.

The QA driver is allowed to automate navigation and screenshots; it must not fabricate UI state. Screens must be rendered by the actual remapped mod in a real Fabric client.


## Targeted Duel Realm completion gate

Production visual QA must prove the runtime systems below, not merely menu rendering:

- spectator mode uses a physical gallery seat in `svarcade_tcg:duel_realm` and exposes no HAND/EXTRA identities;
- previous-turn Set Trap/Counter Trap cards participate in a three-link chain and resolve server-authoritatively;
- Creation advanced summon consumes Palkia + Dialga + Giratina and progresses Arceus Defense -> Arceus Judgement -> Ultimate Arceus;
- admin verification commands are available under `/cardworlds qa spectator|spelltrap|creation`;
- `19-spectator.png` through `25-arceus-ultimate.png` and their runtime log markers are mandatory evidence.
