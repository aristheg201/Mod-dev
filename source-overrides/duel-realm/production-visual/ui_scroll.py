from pathlib import Path
import shutil
here=Path(__file__).resolve().parent
paths={'CardWorldsScreen.java': 'src/main/java/vn/svarcade/tcg/client', 'Ui.java': 'src/main/java/vn/svarcade/tcg/client/component', 'ScrollRegions.java': 'src/main/java/vn/svarcade/tcg/client/component', 'PacksScreen.java': 'src/main/java/vn/svarcade/tcg/client/screens', 'CollectionScreen.java': 'src/main/java/vn/svarcade/tcg/client/screens', 'DeckBuilderScreen.java': 'src/main/java/vn/svarcade/tcg/client/screens', 'MarketScreen.java': 'src/main/java/vn/svarcade/tcg/client/screens', 'PlayScreen.java': 'src/main/java/vn/svarcade/tcg/client/screens', 'PackReveal.java': 'src/main/java/vn/svarcade/tcg/client/animation', 'PackPreviews.java': 'src/main/java/vn/svarcade/tcg/data', 'ScrollRegionsTest.java': 'src/test/java/vn/svarcade/tcg/client/component', 'PackPreviewsTest.java': 'src/test/java/vn/svarcade/tcg/data', 'VisualRun.java': 'src/qa/java/vn/svarcade/tcg/qa'}
for name,directory in paths.items():
    target=Path(directory)/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(here/'ui-java'/name,target)
p=Path('src/main/java/vn/svarcade/tcg/fabric/TcgMod.java');s=p.read_text()
if 'String previewCard' not in s:
    s=s.replace('boolean guaranteed,List<String> rates){}','boolean guaranteed,List<String> rates,String previewCard){}')
    s=s.replace('    public record BannerView','    private Catalog previewCatalog;private Map<String,String> packPreviews=Map.of();\n    public record BannerView')
    s=s.replace('List<BannerView> banners=new ArrayList<>();','if(previewCatalog!=catalog){packPreviews=vn.svarcade.tcg.data.PackPreviews.select(catalog);previewCatalog=catalog;}\n        List<BannerView> banners=new ArrayList<>();')
    s=s.replace('pity.guaranteed(),rates));','pity.guaranteed(),rates,packPreviews.getOrDefault(id,"")));')
p.write_text(s,encoding='utf-8')
