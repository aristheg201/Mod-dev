# Editable Blockbench sources

These files are authoring sources and are intentionally outside `src/main/resources`, so they are not packaged into the production JAR.

- `mysterious_figure.bbmodel` — restrained humanoid phase-one actor.
- `toba_phase2.bbmodel` — TOBA phase-two rig with torso/core/claws/limbs/four tendrils and authored animation tracks.
- `trainer_phone.bbmodel` — editable low-poly Trainer Phone shell.

Runtime textures/models remain first-party assets inside the mod JAR. The TOBA runtime Java model uses the same semantic bone names and attack poses as this Blockbench source.
