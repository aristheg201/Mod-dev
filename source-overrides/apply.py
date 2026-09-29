from pathlib import Path
import shutil

root = Path(".")
over = root / "source-overrides"

copies = {
    over / "DuelWorldScene.java": root / "src/main/java/vn/svarcade/tcg/client/render/DuelWorldScene.java",
    over / "DuelScreen.java": root / "src/main/java/vn/svarcade/tcg/client/screens/DuelScreen.java",
    over / "PokemonModels.java": root / "src/main/java/vn/svarcade/tcg/client/render/PokemonModels.java",
}
for src, dst in copies.items():
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

gp = root / "gradle.properties"
s = gp.read_text()
import re
s = re.sub(r"^loom_version=.*$", "loom_version=1.10.5", s, flags=re.M)
gp.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/client/CardWorldsScreen.java"
s = p.read_text()
old = '''        ui.fill(new Rect(0,0,1280,logicalHeight),Ui.BG);
        if(state.duel()!=null&&!duelPage.dismissed){duelPage.render(this,ui,new Rect(0,0,1280,logicalHeight));}
        else {shell(ui);pages.get(page).render(this,ui,new Rect(192,76,1068,logicalHeight-96));}'''
new = '''        boolean duelActive=state.duel()!=null&&!duelPage.dismissed;
        if(duelActive){duelPage.render(this,ui,new Rect(0,0,1280,logicalHeight));}
        else {ui.fill(new Rect(0,0,1280,logicalHeight),Ui.BG);shell(ui);pages.get(page).render(this,ui,new Rect(192,76,1068,logicalHeight-96));}'''
if old not in s:
    raise SystemExit("CardWorldsScreen render anchor missing")
s = s.replace(old, new)
old = '''    @Override public boolean shouldPause(){return false;}
}'''
new = '''    @Override public void close(){duelPage.closeScene();super.close();}
    @Override public boolean shouldPause(){return false;}
}'''
if old not in s:
    raise SystemExit("CardWorldsScreen close anchor missing")
p.write_text(s.replace(old, new))

p = root / "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java"
s = p.read_text()
repls = [
    ('private long next;private int step;private boolean worldStarted;private int rounds;',
     'private long next;private int step;private boolean worldStarted;private int rounds;private int preWorldPasses;'),
    ('@Override public void onInitializeClient(){if(!Boolean.getBoolean("cardworlds.qa"))return;ClientTickEvents.END_CLIENT_TICK.register(this::tick);}',
     '@Override public void onInitializeClient(){org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_DRIVER_LOADED");ClientTickEvents.END_CLIENT_TICK.register(this::tick);}'),
    ('if(c.player==null){if(c.currentScreen instanceof TitleScreen&&!worldStarted){worldStarted=true;c.createIntegratedServerLoader().start("cardworlds-qa",()->{});}return;}',
     'if(c.player==null){if(!worldStarted&&c.currentScreen!=null){if(++preWorldPasses<10)return;worldStarted=true;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_CREATE_WORLD screen={}",c.currentScreen.getClass().getName());var info=new net.minecraft.world.level.LevelInfo("Card Worlds QA",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);c.createIntegratedServerLoader().createAndStart("cardworlds-qa",info,net.minecraft.world.gen.GeneratorOptions.createRandom(),registries->registries.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.DEFAULT).createDimensionsRegistryHolder(),new TitleScreen());}return;}'),
    ('case 18->{shot(c,"14-duel-result");a.close();step=20;}',
     'case 18->{shot(c,"14-duel-result");((net.minecraft.client.gui.screen.Screen)a).close();step=20;}'),
    ('case 21->{shot(c,"15-reopen-y");a.keyPressed(GLFW.GLFW_KEY_Y,0,0);step=22;}',
     'case 21->{shot(c,"15-reopen-y");((net.minecraft.client.gui.screen.Screen)a).keyPressed(GLFW.GLFW_KEY_Y,0,0);step=22;}'),
    ("""case 24->{shot(c,"16-reopen-command");a.navigate("Collection");a.fields.put("search","");a.focus="search";a.charTyped('y',0);if(!a.fields.get("search").equals("y"))throw new AssertionError("Typing Y failed");step++;}""",
     """case 24->{shot(c,"16-reopen-command");a.navigate("Collection");a.fields.put("search","");a.focus="search";((net.minecraft.client.gui.screen.Screen)a).charTyped('y',0);if(!a.fields.get("search").equals("y"))throw new AssertionError("Typing Y failed");step++;}"""),
    ('private static void click(CardWorldsScreen a,int x,int y){a.mouseClicked(x*a.width/1280.0,y*a.width/1280.0,0);}',
     'private static void click(CardWorldsScreen a,int x,int y){double scale=MinecraftClient.getInstance().getWindow().getScaledWidth()/1280.0;((net.minecraft.client.gui.screen.Screen)a).mouseClicked(x*scale,y*scale,0);}')
]
for old, new in repls:
    if old not in s:
        raise SystemExit("VisualRun anchor missing: " + old[:60])
    s = s.replace(old, new)
p.write_text(s)
