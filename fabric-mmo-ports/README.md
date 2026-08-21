# Fabric MMO Ports

Clean-room Fabric 1.21.1 compatibility ports targeting Fabric Loader >= 0.18.4:

- MythicLib-Fabric (compat profile 1.7.1-SNAPSHOT)
- MMOCore-Fabric (compat profile 1.13.1-SNAPSHOT)
- MythicMobs-Fabric (compat profile 5.6.2-3e052553)
- MMOItems-Fabric (compat profile 6.10.1-SNAPSHOT)

The four mods remain independent. This branch is the active porting workspace. Builds and corpus tests are run locally because GitHub Actions minutes are intentionally not used.

Current archive regression baseline:

- MythicLib: 1026 skills, 104 scripts
- MMOCore: 70 classes, 8 professions, 3 quests
- MythicMobs: 4017 skills, 849 mobs, 892 items, 26492 compiled skill lines
- MMOItems: 3277 item templates, 798 abilities, 88 crafting stations, 6640 recipes
- Anti-Mage Knight vertical regression: PASS
- MMOCore class progression/cast rollback: PASS
- MMOCore profession reusable EXP sources: PASS
- MMOCore quest progress runtime: PASS
- MMOItems signed payload/crafting regression: PASS
- MythicMobs current corpus platform mechanic lines: 13041/13041 native execution paths

`100%` is reserved for behavior proven against the original plugin bytecode/API surface. Corpus coverage alone is not treated as full plugin parity.
