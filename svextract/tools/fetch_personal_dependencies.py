#!/usr/bin/env python3
"""Fetch pinned upstream JARs for the user's explicitly requested local personal bundle.
No remote uploads, publishing, asset extraction, or changes to existing Minecraft installations.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PINNED = {
    "pointblank": ("og4KPYmA", "2.2.0"),
    "geckolib": ("geckolib", "4.9.3"),
    "fabric-language-kotlin": ("fabric-language-kotlin", "1.14.2+kotlin.2.4.21"),
    "cobblemon": ("cobblemon", "1.8.2"),
    "placeholder-api": ("placeholder-api", "2.4.2+1.21"),
    "fabric-permissions-api": ("fabric-permissions-api", "0.3.1"),
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cobblemon", action="store_true")
    args = parser.parse_args()
    destination = ROOT / "deps"
    destination.mkdir(exist_ok=True)
    manifest = []
    for name, (slug, version) in PINNED.items():
        if name == "cobblemon" and not args.cobblemon:
            continue
        url = f"https://api.modrinth.com/v2/project/{slug}/version?game_versions=%5B%221.21.1%22%5D&loaders=%5B%22fabric%22%5D"
        with urllib.request.urlopen(url, timeout=60) as response:
            versions = json.load(response)
        definition = next(v for v in versions if v["version_number"] == version)
        artifact = next(f for f in definition["files"] if f["primary"])
        path = destination / artifact["filename"]
        expected = artifact["hashes"]["sha512"]
        if not path.exists() or hashlib.sha512(path.read_bytes()).hexdigest() != expected:
            partial = path.with_suffix(".partial")
            with urllib.request.urlopen(artifact["url"], timeout=120) as response, partial.open("wb") as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            if hashlib.sha512(partial.read_bytes()).hexdigest() != expected:
                partial.unlink()
                raise RuntimeError(f"Checksum mismatch for {path.name}")
            partial.replace(path)
        manifest.append({"id": name, "version": version, "source": artifact["url"], "sha512": expected, "file": path.name})
        print(f"Verified {path.name}")
    # Exact upstream-authored legacy room for the user's private Haven-9 world.
    map_info = json.loads((ROOT / "qa/reports/vault-map-source.json").read_text())
    map_dir = destination / "maps"
    map_dir.mkdir(exist_ok=True)
    map_file = map_dir / "vault_legacy_haven.nbt"
    if not map_file.exists() or hashlib.sha256(map_file.read_bytes()).hexdigest() != map_info["sha256"]:
        with urllib.request.urlopen(map_info["sourceUrl"], timeout=60) as response:
            data = response.read(2 * 1024 * 1024 + 1)
        if len(data) > 2 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != map_info["sha256"]:
            raise RuntimeError("Private Vault map source size/checksum mismatch")
        map_file.write_bytes(data)
    (map_dir / "vault_legacy_haven.json").write_text(json.dumps(map_info, indent=2) + "\n")
    print("Verified authored private Vault Haven map")
    (destination / "personal-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

if __name__ == "__main__":
    main()
