#!/usr/bin/env python3
"""Explicit, non-destructive merger for older SVEXTRACT loot configs.

Existing caches keep their custom weights and item lists unchanged. Only new
cache IDs are appended from the shipped default catalog. A timestamped backup
is written and the replacement is atomic. Never run while server is active.
"""
import argparse
import datetime
import json
import os
import pathlib
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULTS = ROOT / "config/svextract/loot.json"


def read_config(path):
    with path.open("r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict) or not isinstance(value.get("caches"), list):
        raise ValueError(f"{path}: expected JSON object with caches array")
    if not all(isinstance(c, dict) and isinstance(c.get("id"), str) for c in value["caches"]):
        raise ValueError(f"{path}: every cache must have a string id")
    if len({c["id"] for c in value["caches"]}) != len(value["caches"]):
        raise ValueError(f"{path}: duplicate cache IDs")
    return value


def merge(existing, catalog):
    known = {cache["id"] for cache in existing["caches"]}
    missing = [cache for cache in catalog["caches"] if cache["id"] not in known]
    merged = dict(existing)
    merged["caches"] = existing["caches"] + missing
    if len(merged["caches"]) > 32:
        raise ValueError("Merged cache count exceeds LootConfig limit (32)")
    return merged, missing


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=pathlib.Path, required=True, help="Server config/svextract/loot.json")
    parser.add_argument("--dry-run", action="store_true", help="Print additions without writing")
    args = parser.parse_args()
    path = args.config.expanduser().resolve()
    catalog = read_config(DEFAULTS)
    existing = read_config(path)
    merged, missing = merge(existing, catalog)
    if not missing:
        print("Already current: no cache IDs missing, no write performed")
        return
    print(f"Will add {len(missing)} caches: " + ", ".join(c["id"] for c in missing))
    if args.dry_run:
        return
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup = path.with_name(path.name + ".backup-" + stamp)
    with backup.open("xb") as out:
        out.write(path.read_bytes())
        out.flush()
        os.fsync(out.fileno())
    payload = (json.dumps(merged, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    fd, temp_name = tempfile.mkstemp(prefix="." + path.name + "-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(payload)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
    print(f"Updated {path}; original preserved as {backup}")


if __name__ == "__main__":
    main()
