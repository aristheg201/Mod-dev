from __future__ import annotations

import base64
import io
import shutil
import subprocess
import tarfile
import tempfile
from pathlib import Path

root = Path.cwd()
here = root / "source-overrides" / "duel-realm"
parts = sorted(here.glob("bundle.b64.part*"))
if not parts:
    raise SystemExit("Duel Realm bundle parts are missing")

encoded = b"".join(p.read_bytes().strip() for p in parts)
payload = base64.b64decode(encoded, validate=True)

with tempfile.TemporaryDirectory(prefix="cardworlds-duelrealm-") as tmp_name:
    tmp = Path(tmp_name)
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz") as archive:
        archive.extractall(tmp)

    new_files = {
        "DuelRealmService.java": root / "src/main/java/vn/svarcade/tcg/fabric/DuelRealmService.java",
        "DuelColiseumStructure.java": root / "src/main/java/vn/svarcade/tcg/fabric/DuelColiseumStructure.java",
        "SummonFramework.java": root / "src/main/java/vn/svarcade/tcg/duel/SummonFramework.java",
        "SpellTrapRules.java": root / "src/main/java/vn/svarcade/tcg/duel/SpellTrapRules.java",
        "duel_realm_dimension_type.json": root / "src/main/resources/data/svarcade_tcg/dimension_type/duel_realm.json",
        "duel_realm_dimension.json": root / "src/main/resources/data/svarcade_tcg/dimension/duel_realm.json",
        "summon_profiles.json": root / "src/main/resources/data/svarcade_tcg/summon_profiles.json",
        "spell_trap_profiles.json": root / "src/main/resources/data/svarcade_tcg/spell_trap_profiles.json",
    }
    for name, destination in new_files.items():
        source = tmp / "new" / name
        if not source.is_file():
            raise SystemExit(f"Duel Realm bundle missing {name}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)

    # Runtime hot-path overrides are kept uncompressed so performance fixes do
    # not require repacking the verified rules/engine bundle.
    for name in ("DuelColiseumStructure.java", "DuelRealmService.java"):
        override = here / name
        if not override.is_file():
            raise SystemExit(f"Duel Realm runtime override missing {name}")
        shutil.copyfile(override, new_files[name])

    patch_order = [
        "catalog_java.patch",
        "catalog_json.patch",
        "duel_engine.patch",
        "tcg_mod.patch",
        "commands.patch",
        "messages.patch",
        "duel_world_scene.patch",
        "duel_screen.patch",
        "engine_test.patch",
        "visual_run.patch",
        "qa_gates.patch",
    ]
    for name in patch_order:
        patch_file = tmp / "patches" / name
        if not patch_file.is_file():
            raise SystemExit(f"Duel Realm bundle missing patch {name}")
        subprocess.run(
            ["patch", "-p0", "--forward", "--batch", "-i", str(patch_file)],
            cwd=root,
            check=True,
        )

# A Set card leaves the hand, so spectator-visible hand count must decrement while identity stays hidden.
engine_test = root / "src/test/java/vn/svarcade/tcg/EngineTest.java"
test_source = engine_test.read_text()
old = 'assertEquals(5,d.spectatorView().handCounts().getFirst());'
new = 'assertEquals(4,d.spectatorView().handCounts().getFirst());'
if old not in test_source:
    raise SystemExit("Spectator hand-count regression assertion anchor missing")
engine_test.write_text(test_source.replace(old, new, 1))

print("Applied Duel Realm production bundle")
