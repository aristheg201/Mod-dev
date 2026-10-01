from pathlib import Path
import shutil
root=Path.cwd()
here=Path(__file__).resolve().parent
paths={
 'DuelWorldScene.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelCardMeshes.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelModelBounds.java':'src/main/java/vn/svarcade/tcg/client/render',
 'DuelScreen.java':'src/main/java/vn/svarcade/tcg/client/screens',
 'CobblemonCatalogHydrator.java':'src/main/java/vn/svarcade/tcg/fabric',
 'DuelColiseumStructure.java':'src/main/java/vn/svarcade/tcg/fabric',
 'CobblemonCatalogHydratorTest.java':'src/test/java/vn/svarcade/tcg/fabric',
 'VisualRun.java':'src/qa/java/vn/svarcade/tcg/qa',
}
for name,directory in paths.items():
 target=root/directory/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(here/'java'/name,target)
shutil.copytree(here/'resources',root/'src/main/resources',dirs_exist_ok=True)
shutil.copytree(here/'tools',root/'tools',dirs_exist_ok=True)
print('Applied final Card Worlds textured cards, face-up Pokemon, measured grounding, content, and settled visual proof overlay')
