from __future__ import annotations

import base64
import gzip
import io
import hashlib
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

# Cross-dimension respawn replaces the active GUI with Minecraft's terrain
# loading screen. Keep an explicit client override that reopens the Duel HUD
# after the client world has switched to the Realm.
client_override = here / "TcgClient.java"
client_target = root / "src/main/java/vn/svarcade/tcg/fabric/TcgClient.java"
if not client_override.is_file():
    raise SystemExit("Duel Realm client lifecycle override missing")
shutil.copyfile(client_override, client_target)

# Mark duelists for an explicit reopen snapshot as a second server-side guard.
tcg_mod = root / "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java"
tcg_source = tcg_mod.read_text()
old_prepare = 'private void prepareRealm(MinecraftServer server,Match m){if(duelRealm==null)duelRealm=new DuelRealmService(server);m.arena=duelRealm.allocate(m.id);ServerPlayerEntity a=server.getPlayerManager().getPlayer(m.a);if(a!=null)duelRealm.enterDuelist(a,m.arena,0);if(!m.npc){ServerPlayerEntity b=server.getPlayerManager().getPlayer(m.b);if(b!=null)duelRealm.enterDuelist(b,m.arena,1);}}'
new_prepare = 'private void prepareRealm(MinecraftServer server,Match m){if(duelRealm==null)duelRealm=new DuelRealmService(server);m.arena=duelRealm.allocate(m.id);openRequests.add(m.a);ServerPlayerEntity a=server.getPlayerManager().getPlayer(m.a);if(a!=null)duelRealm.enterDuelist(a,m.arena,0);if(!m.npc){openRequests.add(m.b);ServerPlayerEntity b=server.getPlayerManager().getPlayer(m.b);if(b!=null)duelRealm.enterDuelist(b,m.arena,1);}}'
if old_prepare not in tcg_source:
    raise SystemExit("Duel Realm reopen prepare anchor missing")
tcg_mod.write_text(tcg_source.replace(old_prepare, new_prepare, 1))

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


# Final production overrides: deterministic spectator / Spell-Trap / Creation runtime QA.
final_parts = [here / f"final-overrides.b64.part{i:02d}" for i in range(3)]
final_paths = [
    "src/main/java/vn/svarcade/tcg/duel/Duel.java",
    "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java",
    "src/main/java/vn/svarcade/tcg/fabric/CardWorldsCommands.java",
    "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java",
    "src/test/java/vn/svarcade/tcg/EngineTest.java",
    "src/main/resources/data/svarcade_tcg/messages.json",
    "src/main/resources/data/svarcade_tcg/spell_trap_profiles.json",
]
if not all(p.is_file() for p in final_parts):
    raise SystemExit("Final Duel Realm QA override base64 parts missing")
final_encoded = b"".join(p.read_bytes().strip() for p in final_parts)
final_payload = base64.b64decode(final_encoded, validate=True)
final_sha256 = hashlib.sha256(final_payload).hexdigest()
if final_sha256 != "2c76eb046020b444f615f080f3b501d543541fe6acce21efc6be5e4b6aa8b2f1":
    raise SystemExit(f"Final Duel Realm QA override checksum mismatch: {final_sha256}")
with tempfile.TemporaryDirectory(prefix="cardworlds-final-overrides-") as final_tmp_name:
    final_tmp = Path(final_tmp_name)
    with tarfile.open(fileobj=io.BytesIO(final_payload), mode="r:gz") as archive:
        archive.extractall(final_tmp)
    for rel in final_paths:
        source = final_tmp / rel
        target = root / rel
        if not source.is_file():
            raise SystemExit(f"Final override missing {rel}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)


# PvE bot may consume one response pass between targeted QA commands.
# Resolve from the authoritative current priority instead of assuming a fixed seat.
duel_target = root / "src/main/java/vn/svarcade/tcg/duel/Duel.java"
duel_source = duel_target.read_text()
old = '''    public synchronized void qaResolveSpellTrapChain(int actor){
        require(chain.size()==3,"Expected a three-link Spell/Trap chain.");
        act(1-actor,new Action("pass","",""),revision);
        act(actor,new Action("pass","",""),revision);
    }'''
new = '''    public synchronized void qaResolveSpellTrapChain(int actor){
        require(!chain.isEmpty(),"Expected an active Spell/Trap chain.");
        int guard=4;
        while(!chain.isEmpty()&&guard-->0)act(priority,new Action("pass","",""),revision);
        require(chain.isEmpty(),"Spell/Trap chain did not resolve.");
    }'''
if old not in duel_source:
    raise SystemExit("QA Spell/Trap resolve priority anchor missing")
duel_target.write_text(duel_source.replace(old,new,1))


# Spectator HUD is strictly read-only. Server authority already rejects spectator
# actions; this client layer also removes selection/action affordances.
duel_screen = root / "src/main/java/vn/svarcade/tcg/client/screens/DuelScreen.java"
duel_screen_source = duel_screen.read_text()

old = '        Duel.View v = a.state.duel();\n'
new = '        Duel.View v = a.state.duel();\n        boolean spectator = a.state.spectator();\n'
if old not in duel_screen_source:
    raise SystemExit("DuelScreen spectator render-state anchor missing")
duel_screen_source = duel_screen_source.replace(old, new, 1)

for old, new, label in [
    (
        '    private void choose(CardWorldsScreen a, Duel.VisibleCard c) {\n',
        '    private void choose(CardWorldsScreen a, Duel.VisibleCard c) {\n        if (a.state.spectator()) return;\n',
        "choose",
    ),
    (
        '    private void perform(CardWorldsScreen a, String action) {\n',
        '    private void perform(CardWorldsScreen a, String action) {\n        if (a.state.spectator()) return;\n',
        "perform",
    ),
    (
        '    private boolean targetAllowed(CardWorldsScreen a, Duel.VisibleCard t) {\n',
        '    private boolean targetAllowed(CardWorldsScreen a, Duel.VisibleCard t) {\n        if (a.state.spectator()) return false;\n',
        "target",
    ),
]:
    if old not in duel_screen_source:
        raise SystemExit(f"DuelScreen spectator {label} anchor missing")
    duel_screen_source = duel_screen_source.replace(old, new, 1)

reset_anchor = 'scene::resetView);'
if reset_anchor not in duel_screen_source:
    raise SystemExit("DuelScreen Reset View anchor missing")
spectator_overlay = '''scene::resetView);
        if (spectator) {
            Rect spectatorPanel = new Rect(x - 2, y - 30, 194, 232);
            u.fill(spectatorPanel, 0xF006111C);
            u.frame(spectatorPanel, Ui.CYAN);
            u.text("SPECTATOR", spectatorPanel.x() + 48, spectatorPanel.y() + 22, 18, Ui.CYAN);
            u.fit("READ ONLY", new Rect(spectatorPanel.x() + 18, spectatorPanel.y() + 55, spectatorPanel.w() - 36, 30), 17, Ui.WHITE);
            u.fit("Hidden hands / Extra Deck stay private.", new Rect(spectatorPanel.x() + 14, spectatorPanel.y() + 95, spectatorPanel.w() - 28, 52), 12, Ui.MUTED);
            u.fit("RMB drag • Wheel zoom", new Rect(spectatorPanel.x() + 14, spectatorPanel.y() + 150, spectatorPanel.w() - 28, 30), 12, Ui.GOLD);
        }'''
duel_screen_source = duel_screen_source.replace(reset_anchor, spectator_overlay, 1)
duel_screen.write_text(duel_screen_source)

# Final Card Worlds v2 snapshot. This is a verified effective-source archive,
# copied last so it cannot drift against earlier line-oriented patches.
production_v2_parts = [here / f"production-v2.b64.part{i:02d}" for i in range(4)]
if not all(p.is_file() for p in production_v2_parts):
    raise SystemExit("Card Worlds production-v2 snapshot parts missing")
production_v2_encoded = b"".join(p.read_bytes().strip() for p in production_v2_parts)
production_v2_payload = base64.b64decode(production_v2_encoded, validate=True)
production_v2_sha256 = hashlib.sha256(production_v2_payload).hexdigest()
if production_v2_sha256 != "31f2344f583e832c63749bae593c852e7e1200f3295574c918692878700a8663":
    raise SystemExit(f"Card Worlds production-v2 snapshot checksum mismatch: {production_v2_sha256}")

production_v2_paths = [
    "src/main/java/vn/svarcade/tcg/duel/Duel.java",
    "src/main/java/vn/svarcade/tcg/client/render/DuelWorldScene.java",
    "src/main/java/vn/svarcade/tcg/client/screens/DuelScreen.java",
    "src/main/java/vn/svarcade/tcg/fabric/DuelColiseumStructure.java",
    "src/main/java/vn/svarcade/tcg/fabric/CobblemonCatalogHydrator.java",
    "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java",
    "src/main/java/vn/svarcade/tcg/fabric/TcgPackets.java",
    "src/main/java/vn/svarcade/tcg/client/CardWorldsScreen.java",
    "src/main/java/vn/svarcade/tcg/client/screens/CollectionScreen.java",
    "src/main/java/vn/svarcade/tcg/client/screens/DeckBuilderScreen.java",
    "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java",
    "src/test/java/vn/svarcade/tcg/EngineTest.java",
    "src/test/java/vn/svarcade/tcg/fabric/CobblemonCatalogHydratorTest.java",
]
with tempfile.TemporaryDirectory(prefix="cardworlds-production-v2-") as production_v2_tmp_name:
    production_v2_tmp = Path(production_v2_tmp_name)
    with tarfile.open(fileobj=io.BytesIO(production_v2_payload), mode="r:gz") as archive:
        archive.extractall(production_v2_tmp)
    for rel in production_v2_paths:
        source = production_v2_tmp / rel
        target = root / rel
        if not source.is_file():
            raise SystemExit(f"Card Worlds production-v2 snapshot missing {rel}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

print("Applied Duel Realm production bundle")
