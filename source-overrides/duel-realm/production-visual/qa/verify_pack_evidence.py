"""Check real client traversal evidence for every production pack, never a projection."""
from pathlib import Path
import json
import re
import struct

runtime = Path("run-focused-visual")
expected = json.loads(Path("source-overrides/duel-realm/production-visual/resources/data/svarcade_tcg/pack_previews.json").read_text())
assert len(expected) == 14
log = (runtime / "logs/latest.log").read_text()
assert "CARDWORLDS_FOCUSED_PACK_SCOPE packs=14" in log
assert "CARDWORLDS_FOCUSED_PACK_QA_COMPLETE packs=14 purchases=0" in log
rows = re.findall(r"CARDWORLDS_FOCUSED_PACK_VERIFIED index=(\d+) id=(\S+) preview=(\S+) slots=(\d+) rates=(\d+) beast=150 hunter=2 beast_cmd=6 hunter_cmd=2 list_offset=(\d+)", log)
assert len(rows) == 14, rows
assert {row[1] for row in rows} == set(expected), rows
images = set()
for index, pack, preview, slots, rates, offset in rows:
    assert preview == expected[pack], (pack, preview, expected[pack])
    assert int(slots) > 0 and int(rates) > 0, (pack, slots, rates)
    name = f"pack-{int(index):02d}-{pack}.png"
    images.add(name)
    data = (runtime / "packs-qa/screenshots" / name).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", name
    assert struct.unpack(">II", data[16:24]) == (1280, 720), name
    assert len(data) >= 20000, (name, len(data))
assert {p.name for p in (runtime / "packs-qa/screenshots").glob("*.png")} == images
assert max(int(row[-1]) for row in rows) > 0, "Pack list was not scrolled"
rendering = log[log.index("CARDWORLDS_FOCUSED_PACK_SCOPE"):]
for error in ("Unable to find a poser", "Could not render Cobblemon card model",
              "CARDWORLDS_VARIANT_UNAVAILABLE", "[Render thread/ERROR]", "[STDERR]: java."):
    assert error not in rendering, next((line for line in rendering.splitlines() if error in line), error)
print("CARDWORLDS_ALL_PACKS_EVIDENCE_PASS packs=14 previews=14 payment_descriptors=14 screenshots=14 purchases=0")
