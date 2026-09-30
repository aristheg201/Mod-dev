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

print("Applied Duel Realm production bundle")
