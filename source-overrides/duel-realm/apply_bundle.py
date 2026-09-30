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

# Keep the duel UI alive across respawn/dimension packets. Minecraft closes screens
# during dimension transfer, so snapshots sent after teleport/restore must carry an
# explicit reopen request. Cleanup happens only after the final post-restore snapshot.
tcg = root / "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java"
tcg_source = tcg.read_text()

old = 'matches.put(m.a,m);matches.put(m.b,m);challenges.remove(player);prepareRealm(p.getServer(),m);broadcast(p.getServer(),m);return;'
new = 'matches.put(m.a,m);matches.put(m.b,m);challenges.remove(player);prepareRealm(p.getServer(),m);openRequests.add(m.a);openRequests.add(m.b);broadcast(p.getServer(),m);return;'
if old not in tcg_source:
    raise SystemExit("PvP Duel Realm reopen anchor missing")
tcg_source = tcg_source.replace(old, new, 1)

old = 'matches.put(player,m);prepareRealm(p.getServer(),m);notice=difficulty;'
new = 'matches.put(player,m);prepareRealm(p.getServer(),m);openRequests.add(player);notice=difficulty;'
if old not in tcg_source:
    raise SystemExit("PvE Duel Realm reopen anchor missing")
tcg_source = tcg_source.replace(old, new, 1)

old = '''        LinkedHashSet<UUID> viewers=new LinkedHashSet<>();viewers.add(m.a);if(!m.npc)viewers.add(m.b);viewers.addAll(m.spectators);
        for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,id.equals(winner)?"Victory!":"Duel complete.",0);}
        teardownRealm(server,m);matches.remove(m.a);matches.remove(m.b);
'''
new = '''        LinkedHashSet<UUID> viewers=new LinkedHashSet<>();viewers.add(m.a);if(!m.npc)viewers.add(m.b);viewers.addAll(m.spectators);
        teardownRealm(server,m);
        openRequests.addAll(viewers);
        for(UUID id:viewers){var p=server.getPlayerManager().getPlayer(id);if(p!=null)send(p,id.equals(winner)?"Victory!":"Duel complete.",0);}
        for(UUID id:new LinkedHashSet<>(m.spectators))spectating.remove(id);
        m.spectators.clear();
        matches.remove(m.a);matches.remove(m.b);
'''
if old not in tcg_source:
    raise SystemExit("Duel finish restore/reopen anchor missing")
tcg_source = tcg_source.replace(old, new, 1)

old = 'private void teardownRealm(MinecraftServer server,Match m){if(duelRealm==null)return;for(UUID id:new LinkedHashSet<>(m.spectators)){spectating.remove(id);ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}m.spectators.clear();for(UUID id:List.of(m.a,m.b)){ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}duelRealm.release(m.arena);}'
new = 'private void teardownRealm(MinecraftServer server,Match m){if(duelRealm==null)return;for(UUID id:new LinkedHashSet<>(m.spectators)){ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}for(UUID id:List.of(m.a,m.b)){ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);if(player!=null)duelRealm.restore(player);}duelRealm.release(m.arena);}'
if old not in tcg_source:
    raise SystemExit("Duel Realm teardown lifecycle anchor missing")
tcg_source = tcg_source.replace(old, new, 1)
tcg.write_text(tcg_source)

# Runtime QA must fail fast instead of idling for the outer 300-second timeout.
visual = root / "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java"
visual_source = visual.read_text()
old = 'private long next;private int step;private boolean worldStarted;private int rounds;private int preWorldPasses;'
new = 'private long next;private int step;private boolean worldStarted;private int rounds;private int preWorldPasses;private long duelDeadline;'
if old not in visual_source:
    raise SystemExit("VisualRun deadline field anchor missing")
visual_source = visual_source.replace(old, new, 1)

old = 'if(!(c.currentScreen instanceof CardWorldsScreen a)){if(step==20)'
new = 'if(!(c.currentScreen instanceof CardWorldsScreen a)){if(step==14&&duelDeadline>0&&now>duelDeadline)throw new AssertionError("Duel UI did not reopen after dimension transfer; world="+(c.world==null?"null":c.world.getRegistryKey().getValue()));if(step==20)'
if old not in visual_source:
    raise SystemExit("VisualRun screen lifecycle anchor missing")
visual_source = visual_source.replace(old, new, 1)

old = 'case 13->{shot(c,"11-pack-summary");click(a,640,a.logicalHeight-55);a.navigate("Play");a.send("pve",a.deckName,"HARD");step++;}'
new = 'case 13->{shot(c,"11-pack-summary");click(a,640,a.logicalHeight-55);a.navigate("Play");duelDeadline=now+20000;a.send("pve",a.deckName,"HARD");step++;}'
if old not in visual_source:
    raise SystemExit("VisualRun duel start deadline anchor missing")
visual_source = visual_source.replace(old, new, 1)

old = 'case 14->{if(a.state.duel()==null)return;if(c.world==null||!c.world.getRegistryKey().getValue().toString().equals("svarcade_tcg:duel_realm"))return;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_DUEL_REALM_CONFIRMED world={}",c.world.getRegistryKey().getValue());'
new = 'case 14->{if(a.state.duel()==null){if(now>duelDeadline)throw new AssertionError("PvE duel snapshot missing; notice="+a.state.notice());return;}String qaWorld=c.world==null?"null":c.world.getRegistryKey().getValue().toString();if(!qaWorld.equals("svarcade_tcg:duel_realm")){if(now>duelDeadline)throw new AssertionError("Duel snapshot exists but client world did not transfer: "+qaWorld);return;}duelDeadline=0;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_DUEL_REALM_CONFIRMED world={}",c.world.getRegistryKey().getValue());'
if old not in visual_source:
    raise SystemExit("VisualRun Duel Realm confirmation anchor missing")
visual_source = visual_source.replace(old, new, 1)
visual.write_text(visual_source)

print("Applied Duel Realm production bundle")
