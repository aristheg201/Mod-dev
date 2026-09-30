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

# Server-authoritative duel-session lifetime. Disconnect no longer equals surrender:
# the match is paused for a bounded reconnect grace period, then the absent seat concedes.
p = root / "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java"
s = p.read_text()
old = '    private static final Gson JSON=new Gson();\n'
new = '    private static final Gson JSON=new Gson();\n    private static final long RECONNECT_GRACE_MS=120_000L;\n'
if old not in s:
    raise SystemExit("TcgMod constant anchor missing")
s = s.replace(old, new)

old = '''    private static final class Match {
        String id=UUID.randomUUID().toString(); UUID a,b; Duel duel;boolean ranked,npc;long activity=System.currentTimeMillis();
        int seat(UUID id){return id.equals(a)?0:1;}
    }'''
new = '''    private static final class Match {
        String id=UUID.randomUUID().toString(); UUID a,b; Duel duel;boolean ranked,npc;long activity=System.currentTimeMillis();
        long aDisconnectedAt=-1L,bDisconnectedAt=-1L;
        int seat(UUID id){return id.equals(a)?0:1;}
        UUID opponent(UUID id){return id.equals(a)?b:a;}
        void disconnected(UUID id,long at){if(id.equals(a))aDisconnectedAt=at;else if(id.equals(b))bDisconnectedAt=at;}
        void connected(UUID id){if(id.equals(a))aDisconnectedAt=-1L;else if(id.equals(b))bDisconnectedAt=-1L;}
        boolean humanDisconnected(){return aDisconnectedAt>=0||(!npc&&bDisconnectedAt>=0);}
        int expiredSeat(long now){
            if(aDisconnectedAt>=0&&now-aDisconnectedAt>=RECONNECT_GRACE_MS)return 0;
            if(!npc&&bDisconnectedAt>=0&&now-bDisconnectedAt>=RECONNECT_GRACE_MS)return 1;
            return -1;
        }
    }'''
if old not in s:
    raise SystemExit("TcgMod Match anchor missing")
s = s.replace(old, new)

old = '        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{UUID id=handler.player.getUuid();Match m=matches.get(id);if(m!=null){m.duel.act(m.seat(id),new Duel.Action("concede","",""),m.duel.revision());finish(server,m);}challenges.remove(id);challenges.entrySet().removeIf(e->e.getValue().challenger.equals(id));rateLimit.remove(id);});'
new = '''        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            UUID id=handler.player.getUuid();Match m=matches.get(id);
            if(m!=null&&m.duel.winner()<0){
                m.connected(id);m.activity=System.currentTimeMillis();openRequests.add(id);
                send(handler.player,"Reconnected. Your duel is still active.",0);
                ServerPlayerEntity other=server.getPlayerManager().getPlayer(m.opponent(id));
                if(other!=null)send(other,handler.player.getName().getString()+" reconnected. Duel resumed.",0);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            UUID id=handler.player.getUuid();Match m=matches.get(id);
            if(m!=null&&m.duel.winner()<0){
                long now=System.currentTimeMillis();m.disconnected(id,now);m.activity=now;
                ServerPlayerEntity other=server.getPlayerManager().getPlayer(m.opponent(id));
                if(other!=null)send(other,handler.player.getName().getString()+" disconnected. Duel paused for 120 seconds awaiting reconnect.",0);
            }
            challenges.remove(id);challenges.entrySet().removeIf(e->e.getValue().challenger.equals(id));rateLimit.remove(id);
        });'''
if old not in s:
    raise SystemExit("TcgMod disconnect anchor missing")
s = s.replace(old, new)

old = '''        ServerTickEvents.END_SERVER_TICK.register(server->{if(++tick%20==0)for(Match m:new HashSet<>(matches.values())){
            if(System.currentTimeMillis()-m.activity>600_000){m.duel.act(m.duel.view(0).priority(),new Duel.Action("concede","",""),m.duel.revision());finish(server,m);continue;}
            if(m.npc&&m.duel.winner()<0&&m.duel.view(1).priority()==1){bot(m);broadcast(server,m);}
        }});'''
new = '''        ServerTickEvents.END_SERVER_TICK.register(server->{if(++tick%20==0)for(Match m:new HashSet<>(matches.values())){
            long now=System.currentTimeMillis();
            int expired=m.expiredSeat(now);
            if(expired>=0&&m.duel.winner()<0){
                m.duel.act(expired,new Duel.Action("concede","",""),m.duel.revision());finish(server,m);continue;
            }
            if(!m.humanDisconnected()&&now-m.activity>600_000){m.duel.act(m.duel.view(0).priority(),new Duel.Action("concede","",""),m.duel.revision());finish(server,m);continue;}
            if(!m.humanDisconnected()&&m.npc&&m.duel.winner()<0&&m.duel.view(1).priority()==1){bot(m);broadcast(server,m);}
        }});'''
if old not in s:
    raise SystemExit("TcgMod tick anchor missing")
s = s.replace(old, new)

old = '                case "duel" -> {check(a.size()==3,"Choose a duel action.");Match m=matches.get(player);check(m!=null,"You are not in a duel.");m.duel.act(m.seat(player),new Duel.Action(a.get(0),a.get(1),a.get(2)),r.revision());m.activity=System.currentTimeMillis();broadcast(p.getServer(),m);return;}'
new = '''                case "duel" -> {
                    check(a.size()==3,"Choose a duel action.");Match m=matches.get(player);check(m!=null,"You are not in a duel.");
                    check(!m.humanDisconnected(),"Duel paused while a player reconnects.");
                    m.duel.act(m.seat(player),new Duel.Action(a.get(0),a.get(1),a.get(2)),r.revision());m.activity=System.currentTimeMillis();broadcast(p.getServer(),m);return;
                }'''
if old not in s:
    raise SystemExit("TcgMod duel action anchor missing")
s = s.replace(old, new)
p.write_text(s)

# Do not allow the Card Worlds hotkey to dismiss a live duel.
p = root / "src/main/java/vn/svarcade/tcg/fabric/TcgClient.java"
s = p.read_text()
old = '  ClientTickEvents.END_CLIENT_TICK.register(client->{while(OPEN.wasPressed()){if(client.currentScreen==null&&client.player!=null)request("open",List.of(),0);else if(client.currentScreen instanceof CardWorldsScreen screen&&!screen.editing())screen.close();}});'
new = '''  ClientTickEvents.END_CLIENT_TICK.register(client->{while(OPEN.wasPressed()){
   if(client.currentScreen==null&&client.player!=null)request("open",List.of(),0);
   else if(client.currentScreen instanceof CardWorldsScreen screen&&!screen.editing()&&!screen.duelActive())screen.close();
  }});'''
if old not in s:
    raise SystemExit("TcgClient hotkey anchor missing")
p.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/client/CardWorldsScreen.java"
s = p.read_text()
old = '''        ui.fill(new Rect(0,0,1280,logicalHeight),Ui.BG);
        if(state.duel()!=null&&!duelPage.dismissed){duelPage.render(this,ui,new Rect(0,0,1280,logicalHeight));}
        else {shell(ui);pages.get(page).render(this,ui,new Rect(192,76,1068,logicalHeight-96));}'''
new = '''        boolean duelActive=state.duel()!=null&&!duelPage.dismissed;
        boolean revealActive=reveal.active();
        if(duelActive){duelPage.render(this,ui,new Rect(0,0,1280,logicalHeight));}
        else {
            ui.fill(new Rect(0,0,1280,logicalHeight),Ui.BG);
            shell(ui);
            if(!revealActive)pages.get(page).render(this,ui,new Rect(192,76,1068,logicalHeight-96));
        }'''
if old not in s:
    raise SystemExit("CardWorldsScreen render anchor missing")
s = s.replace(old, new)
old = '''    @Override public boolean shouldPause(){return false;}
}'''
new = '''    public boolean duelActive(){return state.duel()!=null&&state.duel().winner().isBlank()&&!duelPage.dismissed;}
    public void resetDuelCamera(){duelPage.resetCamera();}
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double deltaX,double deltaY){
        if(duelActive()&&duelPage.freeLookDrag(button,deltaX,deltaY))return true;
        return super.mouseDragged(mouseX,mouseY,button,deltaX,deltaY);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontalAmount,double verticalAmount){
        if(duelActive()&&duelPage.freeLookScroll(verticalAmount))return true;
        return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);
    }
    @Override public boolean shouldCloseOnEsc(){return !duelActive();}
    @Override public void close(){if(duelActive())return;duelPage.closeScene();super.close();}
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

old = 'case 16->{shot(c,"13-duel-board");step++;}'
new = 'case 16->{shot(c,"13-duel-board");((net.minecraft.client.gui.screen.Screen)a).mouseDragged(640,360,1,180,-70);((net.minecraft.client.gui.screen.Screen)a).mouseScrolled(640,360,0,2);next=now+700;step=161;}'
if old not in s:
    raise SystemExit("VisualRun free-look anchor missing")
s = s.replace(old, new)
old = 'case 17->{'
new = 'case 161->{shot(c,"14-duel-freelook");a.resetDuelCamera();next=now+500;step=17;}\n    case 17->{'
if old not in s:
    raise SystemExit("VisualRun case17 anchor missing")
s = s.replace(old, new, 1)
s = s.replace('shot(c,"14-duel-result")','shot(c,"15-duel-result")')
s = s.replace('shot(c,"15-reopen-y")','shot(c,"16-reopen-y")')
s = s.replace('shot(c,"16-reopen-command")','shot(c,"17-reopen-command")')
s = s.replace('shot(c,"17-scale-two")','shot(c,"18-scale-two")')
p.write_text(s)
