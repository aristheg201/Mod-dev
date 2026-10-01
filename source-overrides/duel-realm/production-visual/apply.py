from pathlib import Path
import shutil
root=Path.cwd()
here=Path(__file__).resolve().parent
paths={
 'SnapshotCompression.java':'src/main/java/vn/svarcade/tcg/fabric',
 'EffectSpec.java':'src/main/java/vn/svarcade/tcg/data',
 'EffectContent.java':'src/main/java/vn/svarcade/tcg/data',
 'DuelVfxProfile.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelVfxTimeline.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelVfxRenderer.java':'src/main/java/vn/svarcade/tcg/client/render',
 'PokemonDuelAnimationResolver.java':'src/main/java/vn/svarcade/tcg/client/render',
 'CardWorldsLanguage.java':'src/main/java/vn/svarcade/tcg/client/component',
 'DeepEffectsTest.java':'src/test/java/vn/svarcade/tcg/duel',
 'DuelWorldScene.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelCardMeshes.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelModelBounds.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelScreen.java':'src/main/java/vn/svarcade/tcg/client/screens',
 'CobblemonCatalogHydrator.java':'src/main/java/vn/svarcade/tcg/fabric',
 'DuelColiseumStructure.java':'src/main/java/vn/svarcade/tcg/fabric',
 'CobblemonCatalogHydratorTest.java':'src/test/java/vn/svarcade/tcg/fabric',
 'VisualRun.java':'src/qa/java/vn/svarcade/tcg/qa',
 'DuelPileVisibilityTest.java':'src/test/java/vn/svarcade/tcg',
}
for name,directory in paths.items():
 target=root/directory/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(here/'java'/name,target)
shutil.copytree(here/'resources',root/'src/main/resources',dirs_exist_ok=True)
shutil.copytree(here/'tools',root/'tools',dirs_exist_ok=True)
import runpy
runpy.run_path(str(here/'pile_state.py'),run_name='__main__')
for script in ('catalog_effects.py','duel_engine.py','localize_ui.py'):
 runpy.run_path(str(here/script),run_name='__main__')
packet=root/'src/main/java/vn/svarcade/tcg/fabric/TcgPackets.java'
s=packet.read_text().replace('b.writeString(v.json,2097152),b->new Snapshot(b.readString(2097152))','b.writeByteArray(SnapshotCompression.encode(v.json)),b->new Snapshot(SnapshotCompression.decode(b.readByteArray(900*1024)))')
packet.write_text(s)
print('Applied final Card Worlds textured cards, face-up Pokemon, measured grounding, content, and settled visual proof overlay')
