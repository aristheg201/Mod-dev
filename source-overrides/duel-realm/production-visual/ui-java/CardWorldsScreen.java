package vn.svarcade.tcg.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import vn.svarcade.tcg.client.component.*;
import vn.svarcade.tcg.client.screens.*;
import vn.svarcade.tcg.client.animation.PackReveal;
import vn.svarcade.tcg.fabric.*;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.economy.CardStore;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

public final class CardWorldsScreen extends Screen {
    public TcgMod.Snapshot state;public String page="Home",selected="",deckName="Starter",templateDeck="",format="CASUAL",category="all",rarity="All",set="All",ownership="All",banner="",mode="Casual",peer="";
    public final List<String> main=new ArrayList<>(),extra=new ArrayList<>();public final Set<String> favorites=new HashSet<>();
    public final Map<String,String> fields=new HashMap<>();public String focus="",localNotice="";public int scroll;public boolean details,trade;
    private final List<Ui.Hit> hits=new ArrayList<>();private final Map<String,Page> pages=new LinkedHashMap<>();
    public final PackReveal reveal=new PackReveal();public final DuelScreen duelPage=new DuelScreen();
    private float scale;public int logicalHeight;private boolean selectAll;private long noticeAt;
    private record Menu(Rect anchor,List<String> values,java.util.function.Consumer<String> chosen){}
    private Menu menu;
    public final ScrollRegions scrolling=new ScrollRegions();
    private Rect menuBox;
    public CardWorldsScreen(TcgMod.Snapshot s){super(Text.literal("Card Worlds"));state=s;pages.put("Home",new HomeScreen());pages.put("Play",new PlayScreen());pages.put("Collection",new CollectionScreen());pages.put("Decks",new DeckBuilderScreen());pages.put("Market",new MarketScreen());pages.put("Packs",new PacksScreen());pages.put("World",new WorldScreen());if(!s.savedDecks().isEmpty())loadDeck(s.savedDecks().getFirst().name());readFavorites();}
    public void update(TcgMod.Snapshot next){boolean starter=state.savedDecks().isEmpty()&&!next.savedDecks().isEmpty();if(!next.pulls().isEmpty())reveal.start(next.pulls(),state.counts());duelPage.observe(state.duel(),next.duel());state=next;if(starter)loadDeck(next.savedDecks().getFirst().name());if(!next.notice().isBlank()){localNotice=next.notice();noticeAt=System.currentTimeMillis();}}
    public void navigate(String to){page=to;scroll=0;focus="";details=false;menu=null;scrolling.reset();}
    public boolean editing(){return !focus.isBlank();}
    public void select(Ui ui,String label,Rect r,List<String> values,java.util.function.Consumer<String> chosen){ui.button(label+"  ▾",r,false,true,()->{focus="";scrolling.reset("menu");menu=menu!=null&&menu.anchor.equals(r)?null:new Menu(r,List.copyOf(values),chosen);});}
    public ScrollRegions.Region beginScroll(Ui ui,String id,Rect box,int content){
        Rect visible=ui.clipped(box);var region=scrolling.register(id,visible,Math.max(visible.h(),content));ui.clip(visible);return region;
    }
    public void endScroll(Ui ui,ScrollRegions.Region region){ui.endClip();if(region.max()>0){ui.fill(region.track(),0xFF102638);ui.fill(region.thumb(),Ui.CYAN);}}
    private void menu(Ui ui){if(menu==null)return;Menu current=menu;
        int height=Math.min(current.values.size()*31+8,Math.min(360,logicalHeight-28));
        int y=Math.clamp(current.anchor.bottom()+4,10,logicalHeight-height-10);
        menuBox=new Rect(Math.min(current.anchor.x(),1260-Math.max(150,current.anchor.w())),y,Math.max(150,current.anchor.w()),height);
        ui.c.getMatrices().push();ui.c.getMatrices().translate(0,0,500);ui.fill(menuBox,Ui.BG);ui.panel(menuBox);ui.hits.clear();
        Rect viewport=menuBox.inset(4);var region=beginScroll(ui,"menu",viewport,current.values.size()*31);
        for(int i=0;i<current.values.size();i++){String value=current.values.get(i);Rect row=new Rect(viewport.x(),viewport.y()+i*31-region.offset(),viewport.w()-12,29);
            if(!ui.visible(row))continue;if(row.contains(ui.mx,ui.my))ui.fill(row,0xFF204360);ui.fit(value,row.inset(6),15,Ui.WHITE);
            ui.click(row,()->{current.chosen.accept(value);menu=null;scroll=0;scrolling.reset();});}
        endScroll(ui,region);ui.c.getMatrices().pop();
    }
    public void notice(String s){localNotice=s;noticeAt=System.currentTimeMillis();}
    public void send(String action,String...args){TcgClient.request(action,List.of(args),state.duel()==null?0:state.duel().revision());}
    public void field(Ui ui,String id,String placeholder,Rect r){String value=fields.getOrDefault(id,"");ui.panel(r);if(focus.equals(id))ui.frame(r,Ui.CYAN);ui.fit(value.isBlank()?placeholder:value+(focus.equals(id)&&System.currentTimeMillis()%1000<500?"|":""),r.inset(9),15,value.isBlank()?Ui.MUTED:Ui.WHITE);ui.click(r,()->{focus=id;selectAll=false;});}
    public List<Catalog.Card> cards(){String search=fields.getOrDefault("search","").toLowerCase(Locale.ROOT);return state.definitions().values().stream().filter(c->vn.svarcade.tcg.client.component.CardWorldsLanguage.name(c).toLowerCase(Locale.ROOT).contains(search))
        .filter(c->category.equals("all")||c.category().equals(category)).filter(c->rarity.equals("All")||c.rarity().equals(rarity)).filter(c->set.equals("All")||c.set().equals(set))
        .filter(c->ownership.equals("All")||ownership.equals("Owned")&&count(c.id())>0||ownership.equals("Missing")&&count(c.id())==0||ownership.equals("Favorites")&&favorites.contains(c.id()))
        .sorted(Comparator.comparing((Catalog.Card c)->count(c.id())==0).thenComparing(Catalog.Card::name)).toList();}
    public int count(String id){return state.counts().getOrDefault(id,0);}
    public Catalog.Card selectedCard(){return state.definitions().get(selected);}
    public CardStore.Owned owned(String id){return state.inventory().stream().filter(c->c.card().equals(id)&&c.lock().isEmpty()).findFirst().orElse(null);}
    public Catalog.Card bySerial(String serial){return state.inventory().stream().filter(c->c.serial().equals(serial)).findFirst().map(c->state.definitions().get(c.card())).orElse(null);}
    public void add(Catalog.Card card){var available=state.inventory().stream().filter(c->c.card().equals(card.id())&&c.lock().isEmpty()&&!main.contains(c.serial())&&!extra.contains(c.serial())).findFirst();if(available.isEmpty()){notice("No available copies.");return;}if(card.extra()&&extra.size()>=state.rules().extraDeck()||!card.extra()&&main.size()>=state.rules().maxDeck()){notice("Deck is full.");return;}(card.extra()?extra:main).add(available.get().serial());}
    public void remove(String serial){main.remove(serial);extra.remove(serial);}
    public void loadDeck(String name){state.savedDecks().stream().filter(d->d.name().equals(name)).findFirst().ifPresent(d->{deckName=d.name();fields.put("deckName",deckName);main.clear();main.addAll(d.main());extra.clear();extra.addAll(d.extra());format=state.formats().getOrDefault(name,"CASUAL");});}
    public void loadTemplate(String id){
        List<String> template=state.deckTemplates().get(id);if(template==null)return;
        main.clear();extra.clear();deckName="Template · "+id;fields.put("deckName",deckName);templateDeck=id;
        Map<String,Integer> wanted=new LinkedHashMap<>();template.forEach(card->wanted.merge(card,1,Integer::sum));int missing=0;
        Set<String> used=new HashSet<>();
        for(var e:wanted.entrySet()){var def=state.definitions().get(e.getKey());if(def==null){missing+=e.getValue();continue;}int added=0;for(var owned:state.inventory()){if(added>=e.getValue())break;if(!owned.card().equals(e.getKey())||!owned.lock().isEmpty()||!used.add(owned.serial()))continue;(def.extra()?extra:main).add(owned.serial());added++;}missing+=e.getValue()-added;}
        notice(missing==0?"Template loaded: "+id:"Template preview loaded · "+missing+" card(s) missing from your collection.");
    }
    public void saveDeck(){deckName=fields.getOrDefault("deckName",deckName);List<String> args=new ArrayList<>(List.of(deckName,Integer.toString(main.size()),format));args.addAll(main);args.addAll(extra);TcgClient.request("save_deck",args,0);}
    public List<String> legality(){List<String> errors=new ArrayList<>();if(main.size()<state.rules().minDeck()||main.size()>state.rules().maxDeck())errors.add("Main Deck: "+state.rules().minDeck()+"–"+state.rules().maxDeck()+" cards");if(extra.size()>state.rules().extraDeck())errors.add("Extra Deck: up to "+state.rules().extraDeck());Map<String,Integer> n=new HashMap<>();for(String token:java.util.stream.Stream.concat(main.stream(),extra.stream()).toList()){var c=bySerial(token);if(c==null){errors.add("A card is no longer available");continue;}int limit=format.equals("RANKED")?state.rules().rankedLimits().getOrDefault(c.id(),state.rules().copies()):state.rules().copies();if(n.merge(c.id(),1,Integer::sum)>limit)errors.add(c.name()+": copy limit");}return errors.stream().distinct().toList();}
    public void favorite(String id){if(!favorites.add(id))favorites.remove(id);try{Files.write(FabricLoader.getInstance().getConfigDir().resolve("cardworlds-favorites.txt"),favorites);}catch(Exception ex){notice("Could not save favorites.");}}
    private void readFavorites(){try{Path f=FabricLoader.getInstance().getConfigDir().resolve("cardworlds-favorites.txt");if(Files.exists(f))favorites.addAll(Files.readAllLines(f));}catch(Exception ignored){}}
    public void cycleFilter(String which){switch(which){case "rarity"->{var values=new ArrayList<>(List.of("All"));state.definitions().values().stream().map(Catalog.Card::rarity).distinct().sorted().forEach(values::add);rarity=values.get((values.indexOf(rarity)+1)%values.size());}case "set"->{var v=new ArrayList<>(List.of("All"));state.definitions().values().stream().map(Catalog.Card::set).distinct().sorted().forEach(v::add);set=v.get((v.indexOf(set)+1)%v.size());}case "owned"->{var v=List.of("Owned","Missing","All","Favorites");ownership=v.get((v.indexOf(ownership)+1)%v.size());}}scroll=0;}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        scale=width/1280f;logicalHeight=(int)(height/scale);hits.clear();scrolling.beginFrame();c.getMatrices().push();c.getMatrices().scale(scale,scale,1);Ui ui=new Ui(c,mouseX/scale,mouseY/scale,hits);
        boolean duelActive=state.duel()!=null&&!duelPage.dismissed;
        boolean revealActive=reveal.active();
        if(duelActive){duelPage.render(this,ui,new Rect(0,0,1280,logicalHeight));}
        else {
            ui.fill(new Rect(0,0,1280,logicalHeight),Ui.BG);
            shell(ui);
            if(!revealActive){Rect viewport=new Rect(192,76,1068,Math.max(80,logicalHeight-96));
                int minimum=switch(page){case "Play"->650;case "Home","World"->620;case "Decks"->600;default->560;};
                int content=Math.max(minimum,viewport.h());var region=beginScroll(ui,"page/"+page,viewport,content);
                pages.get(page).render(this,ui,new Rect(viewport.x(),viewport.y()-region.offset(),viewport.w()-12,content));endScroll(ui,region);}
        }
        if(reveal.active())reveal.render(this,ui,new Rect(0,0,1280,logicalHeight));
        if(!localNotice.isBlank()&&System.currentTimeMillis()-noticeAt<5500){Rect toast=new Rect(360,logicalHeight-46,570,34);ui.panel(toast);ui.fit(localNotice,toast.inset(10),15,Ui.GOLD);}
        ui.tooltip();menu(ui);c.getMatrices().pop();
    }
    private void shell(Ui ui){
        ui.c.fillGradient(0,0,178,logicalHeight,0xFF0B1D2C,0xFF030C15);ui.fill(new Rect(178,0,1,logicalHeight),Ui.LINE);ui.fill(new Rect(179,0,1101,57),0xFF0A1927);ui.fill(new Rect(179,56,1101,1),Ui.LINE);
        ui.item(Items.COMPASS,new Rect(16,15,30,30));ui.text("cardworlds.ui.card_worlds",51,24,18,Ui.GOLD);
        int y=88;var icons=List.of(Items.NETHER_STAR,Items.IRON_SWORD,Items.ENCHANTED_BOOK,Items.BOOKSHELF,Items.GOLD_INGOT,Items.ENDER_CHEST,Items.COMPASS);int i=0;
        var navigation=beginScroll(ui,"navigation",new Rect(12,80,158,Math.max(80,logicalHeight-140)),pages.size()*52);y-=navigation.offset();
        for(String name:pages.keySet()){Rect r=new Rect(12,y,154,43);if(page.equals(name)){ui.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF174466,0xFF0B253B);ui.ornament(r,Ui.CYAN);}else if(r.contains(ui.mx,ui.my))ui.panel(r);ui.item(icons.get(i++),new Rect(23,y+10,22,22));ui.text(name,57,y+14,17,page.equals(name)?Ui.WHITE:Ui.MUTED);ui.click(r,()->navigate(name));y+=52;}
        endScroll(ui,navigation);
        ui.text("cardworlds.ui.kanto_crossroads",18,logicalHeight-46,12,Ui.GOLD);ui.text(state.total()+" catalog · "+state.collected()+" collected",18,logicalHeight-27,12,Ui.MUTED);
        ui.item(Items.PLAYER_HEAD,new Rect(196,10,34,34));ui.text(state.playerName(),243,14,18,Ui.WHITE);ui.text("cardworlds.ui.collector",243,35,12,Ui.MUTED);
        CurrencyPurchaseUi.renderBalances(ui,state.currencyBalances());
        ui.item(Items.AMETHYST_SHARD,new Rect(1042,17,21,21));ui.text(state.profile().rating()+" RP",1072,20,18,Ui.WHITE);ui.button("cardworlds.ui.close",new Rect(1180,14,72,29),false,true,this::close);
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);
        if(menu!=null&&menuBox!=null&&!menuBox.contains(x/scale,y/scale)){menu=null;return true;}
        if(scrolling.press(x/scale,y/scale))return true;for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);if(hit.box().contains(x/scale,y/scale)){hit.action().run();return true;}}focus="";return true;}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(reveal.active())return scrolling.wheel(x/scale,y/scale,v);
        if(menu!=null){if(menuBox!=null&&menuBox.contains(x/scale,y/scale))scrolling.wheel(x/scale,y/scale,v);return true;}
        if(duelActive()&&duelPage.freeLookScroll(v))return true;
        return scrolling.wheel(x/scale,y/scale,v);
    }
    @Override public boolean mouseReleased(double x,double y,int button){return scrolling.release()||super.mouseReleased(x,y,button);}
    @Override public boolean charTyped(char chr,int modifiers){if(!focus.isBlank()&&!Character.isISOControl(chr)){String old=selectAll?"":fields.getOrDefault(focus,"");selectAll=false;if(old.length()<64)fields.put(focus,old+chr);scroll=0;scrolling.reset();return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!focus.isBlank()){if(key==GLFW.GLFW_KEY_ESCAPE||key==GLFW.GLFW_KEY_ENTER){focus="";return true;}if(key==GLFW.GLFW_KEY_A&&hasControlDown()){selectAll=true;return true;}if(key==GLFW.GLFW_KEY_BACKSPACE){String v=fields.getOrDefault(focus,"");fields.put(focus,selectAll?"":v.isEmpty()?v:v.substring(0,v.length()-1));selectAll=false;return true;}if(key==GLFW.GLFW_KEY_V&&hasControlDown()){fields.put(focus,client.keyboard.getClipboard().replaceAll("[\\r\\n]","").substring(0,Math.min(64,client.keyboard.getClipboard().replaceAll("[\\r\\n]","").length())));return true;}}
        else if(TcgClient.OPEN.matchesKey(key,scan)){close();return true;}return super.keyPressed(key,scan,mods);}
    public boolean duelActive(){return state.duel()!=null&&state.duel().winner().isBlank()&&!duelPage.dismissed;}
    public void resetDuelCamera(){duelPage.resetCamera();}
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double deltaX,double deltaY){
        if(button==0&&scrolling.drag(mouseY/scale))return true;
        if(duelActive()&&duelPage.freeLookDrag(button,deltaX,deltaY))return true;
        return super.mouseDragged(mouseX,mouseY,button,deltaX,deltaY);
    }
    @Override public boolean shouldCloseOnEsc(){return !duelActive();}
    @Override public void close(){if(duelActive())return;duelPage.closeScene();super.close();}
    @Override public boolean shouldPause(){return false;}
}
