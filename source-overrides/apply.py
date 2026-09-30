from pathlib import Path
import shutil

root = Path(".")
over = root / "source-overrides"

copies = {
    over / "DuelWorldScene.java": root / "src/main/java/vn/svarcade/tcg/client/render/DuelWorldScene.java",
    over / "DuelScreen.java": root / "src/main/java/vn/svarcade/tcg/client/screens/DuelScreen.java",
    over / "PokemonModels.java": root / "src/main/java/vn/svarcade/tcg/client/render/PokemonModels.java",
    over / "PlayScreen.java": root / "src/main/java/vn/svarcade/tcg/client/screens/PlayScreen.java",
    over / "EconomyRewards.java": root / "src/main/java/vn/svarcade/tcg/integration/EconomyRewards.java",
    over / "CardWorldsCommands.java": root / "src/main/java/vn/svarcade/tcg/fabric/CardWorldsCommands.java",
    over / "MessageService.java": root / "src/main/java/vn/svarcade/tcg/fabric/MessageService.java",
    over / "PlaceholderSupport.java": root / "src/main/java/vn/svarcade/tcg/fabric/PlaceholderSupport.java",
    over / "messages.json": root / "src/main/resources/data/svarcade_tcg/messages.json",
}
for src, dst in copies.items():
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

gp = root / "gradle.properties"
s = gp.read_text()
import re
s = re.sub(r"^loom_version=.*$", "loom_version=1.10.5", s, flags=re.M)
gp.write_text(s)

# Yu-Gi-Oh-style Level/Tribute rules + visible card rules text.
p = root / "src/main/java/vn/svarcade/tcg/data/Catalog.java"
s = p.read_text()
old = '''    public record Card(String id, String name, String category, String species, List<String> aspects,
                       String type, String family, String evolvesFrom, boolean extra, int power,
                       String text, String set, String rarity, List<String> sources, Effect effect,
                       List<Trigger> triggers,List<Modifier> modifiers) {}'''
new = '''    public record Card(String id, String name, String category, String species, List<String> aspects,
                       String type, String family, String evolvesFrom, boolean extra, int level, int power,
                       String text, String set, String rarity, List<String> sources, Effect effect,
                       List<Trigger> triggers,List<Modifier> modifiers) {
        public int tributeCount() {
            if(!category.equals("pokemon") || extra || level <= 4) return 0;
            return level <= 6 ? 1 : 2;
        }
        public String summonRequirement() {
            if(!category.equals("pokemon")) return "";
            if(extra) return "Extra Evolution";
            int tributes=tributeCount();
            return tributes==0 ? "No Tribute" : tributes==1 ? "1 Tribute" : "2 Tributes";
        }
    }'''
if old not in s:
    raise SystemExit("Catalog Card schema anchor missing")
s = s.replace(old, new)
old = '            if(!e.getKey().equals(c.id) || c.power < 0 || c.sources.isEmpty()) throw new IllegalArgumentException("Invalid card " + e.getKey());'
new = '''            if(!e.getKey().equals(c.id) || c.power < 0 || c.sources.isEmpty()) throw new IllegalArgumentException("Invalid card " + e.getKey());
            if(c.category.equals("pokemon") && (c.level < 1 || c.level > 12)) throw new IllegalArgumentException("Pokemon level must be 1–12: " + c.id);
            if(!c.category.equals("pokemon") && c.level != 0) throw new IllegalArgumentException("Only Pokemon cards have levels: " + c.id);'''
if old not in s:
    raise SystemExit("Catalog validation anchor missing")
s = s.replace(old, new)
s = s.replace('Set.of("PLAY","EVOLVE","EXTRA_SUMMON","DRAW","DESTROY","BATTLE","BANISH","RETURN","REVIVE","EFFECT","DISCARD","SYSTEM")',
              'Set.of("PLAY","TRIBUTE_SUMMON","EVOLVE","EXTRA_SUMMON","DRAW","DESTROY","BATTLE","BANISH","RETURN","REVIVE","EFFECT","DISCARD","SYSTEM")')
p.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/duel/Duel.java"
s = p.read_text()
old = '    public enum Cause { DRAW, PLAY, EVOLVE, EXTRA_SUMMON, DISCARD, DESTROY, BATTLE, BANISH, RETURN, REVIVE, EFFECT, SYSTEM }'
new = '    public enum Cause { DRAW, PLAY, TRIBUTE, TRIBUTE_SUMMON, EVOLVE, EXTRA_SUMMON, DISCARD, DESTROY, BATTLE, BANISH, RETURN, REVIVE, EFFECT, SYSTEM }'
if old not in s:
    raise SystemExit("Duel cause anchor missing")
s = s.replace(old, new)
old = '''    private void play(int actor,String token,String material) {
        mainAction(actor);Piece p=owned(actor,token);require(p.zone==Zone.HAND||p.zone==Zone.EXTRA,"That card cannot be played here.");
        require(p.card.category().equals("pokemon"),"Use Activate for this card.");
        boolean evolve=p.card.evolvesFrom()!=null&&!p.card.evolvesFrom().isBlank();
        Piece previous=null;
        if(evolve) {previous=owned(actor,material);require(previous.zone==Zone.FIELD&&previous.card.id().equals(p.card.evolvesFrom()),"Choose the required evolution.");}
        else {require(!p.card.extra(),"This form requires an evolution material."); require(count(actor,Zone.FIELD)<catalog.rules().pokemonZones(),"Your field is full.");require(normal[actor]<catalog.rules().normalSummons(),"You have used your normal play this turn.");}
        if(previous!=null) {p.boost=previous.boost;p.shield=previous.shield;p.attacked=previous.attacked;move(previous,Zone.DISCARD,Cause.EVOLVE,p.token,0);}
        else normal[actor]++;
        move(p,Zone.FIELD,p.card.extra()?Cause.EXTRA_SUMMON:evolve?Cause.EVOLVE:Cause.PLAY,token,0);
        note(p.card.name()+" enters the field.");window();
    }'''
new = '''    private void play(int actor,String token,String material) {
        mainAction(actor);Piece p=owned(actor,token);require(p.zone==Zone.HAND||p.zone==Zone.EXTRA,"That card cannot be played here.");
        require(p.card.category().equals("pokemon"),"Use Activate for this card.");
        String evolution=p.card.evolvesFrom()==null?"":p.card.evolvesFrom();
        List<String> materials=materials(material);

        if(p.card.extra()) {
            require(!evolution.isBlank(),"This Extra Deck form has no evolution material.");
            require(materials.size()==1,"Choose the required evolution material.");
            Piece previous=owned(actor,materials.getFirst());
            require(previous.zone==Zone.FIELD&&previous.card.id().equals(evolution),"Choose the required evolution.");
            p.boost=previous.boost;p.shield=previous.shield;p.attacked=previous.attacked;
            move(previous,Zone.DISCARD,Cause.EVOLVE,p.token,0);
            move(p,Zone.FIELD,Cause.EXTRA_SUMMON,token,0);
            note(p.card.name()+" evolves from the Extra Deck.");window();return;
        }

        if(!evolution.isBlank()&&materials.size()==1) {
            Piece candidate=pieces.get(materials.getFirst());
            if(candidate!=null&&candidate.controller==actor&&candidate.zone==Zone.FIELD&&candidate.card.id().equals(evolution)) {
                p.boost=candidate.boost;p.shield=candidate.shield;p.attacked=candidate.attacked;
                move(candidate,Zone.DISCARD,Cause.EVOLVE,p.token,0);
                move(p,Zone.FIELD,Cause.EVOLVE,token,0);
                note(p.card.name()+" evolves from "+candidate.card.name()+".");window();return;
            }
        }

        require(p.zone==Zone.HAND,"Main Deck Pokemon must be summoned from the hand.");
        require(normal[actor]<catalog.rules().normalSummons(),"You have used your Normal Summon this turn.");
        int required=p.card.tributeCount();
        require(materials.size()==required, required==0 ? "This Pokemon does not require a Tribute." :
                "Level "+p.card.level()+" requires "+required+(required==1?" Tribute.":" Tributes."));
        LinkedHashSet<String> unique=new LinkedHashSet<>(materials);
        require(unique.size()==materials.size(),"Choose different Tribute Pokemon.");
        List<Piece> tributes=new ArrayList<>();
        for(String id:materials) {
            Piece tribute=owned(actor,id);
            require(tribute.zone==Zone.FIELD,"Tributes must be your Pokemon on the field.");
            tributes.add(tribute);
        }
        require(count(actor,Zone.FIELD)-tributes.size()<catalog.rules().pokemonZones(),"Your field is full.");
        for(Piece tribute:tributes) move(tribute,Zone.DISCARD,Cause.TRIBUTE,p.token,0);
        normal[actor]++;
        Cause cause=required>0?Cause.TRIBUTE_SUMMON:Cause.PLAY;
        move(p,Zone.FIELD,cause,token,0);
        note(required>0 ? p.card.name()+" is Tribute Summoned." : p.card.name()+" is Normal Summoned.");window();
    }
    private static List<String> materials(String raw) {
        if(raw==null||raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();
    }'''
if old not in s:
    raise SystemExit("Duel play anchor missing")
s = s.replace(old, new)
p.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/client/card/CardRenderer.java"
s = p.read_text()
old = '''    public static void draw(Ui ui,Catalog.Card d,Rect r,int count,boolean selected,boolean owned,String key){
        boolean hover=r.contains(ui.mx,ui.my);int edge=selected?Ui.CYAN:hover?Ui.GOLD:color(d);ui.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF283E51,0xFF07131F);ui.ornament(r,edge);
        ui.fill(new Rect(r.x()+3,r.y()+3,r.w()-6,19),0xEE091522);ui.fit(d.name(),new Rect(r.x()+7,r.y()+5,r.w()-14,17),Math.min(14,Math.max(10,r.w()/9)),Ui.WHITE);
        Rect art=new Rect(r.x()+5,r.y()+24,r.w()-10,r.h()-57);ui.c.fillGradient(art.x(),art.y(),art.right(),art.bottom(),0xFF142B40,0xFF0A1523);
        if(d.category().equals("pokemon"))PokemonModels.draw(ui,d.species(),d.aspects(),art,key);
        else {var item=switch(d.category()){case "item"->Items.POTION;case "trainer"->Items.WRITABLE_BOOK;case "technique"->Items.BLAZE_POWDER;case "reaction"->Items.SHIELD;case "stadium"->Items.BEACON;default->Items.ENCHANTED_BOOK;};int size=Math.min(art.w()-12,art.h()-8);ui.item(item,new Rect(art.x()+(art.w()-size)/2,art.y()+(art.h()-size)/2,size,size));}
        ui.fill(new Rect(r.x()+4,r.bottom()-31,r.w()-8,27),0xEE0B1925);ui.fit(d.category().equals("pokemon")?d.type().toUpperCase()+"  "+d.power():d.category().toUpperCase(),new Rect(r.x()+8,r.bottom()-28,r.w()-16,12),11,edge);ui.fit(d.rarity(),new Rect(r.x()+8,r.bottom()-15,r.w()-32,12),11,Ui.GOLD);if(count>0)ui.text("×"+count,r.right()-28,r.bottom()-16,12,Ui.WHITE);
        if(!owned)ui.fill(r,0x66061320);if(selected)ui.frame(r.inset(2),Ui.CYAN);if(hover)ui.tooltip=d.name()+" — "+d.text();
    }'''
new = '''    public static void draw(Ui ui,Catalog.Card d,Rect r,int count,boolean selected,boolean owned,String key){
        boolean hover=r.contains(ui.mx,ui.my);int edge=selected?Ui.CYAN:hover?Ui.GOLD:color(d);
        ui.c.fillGradient(r.x(),r.y(),r.right(),r.bottom(),0xFF283E51,0xFF07131F);ui.ornament(r,edge);

        boolean pokemon=d.category().equals("pokemon");
        int headerH=pokemon?31:22;
        ui.fill(new Rect(r.x()+3,r.y()+3,r.w()-6,headerH-3),0xEE091522);
        ui.fit(d.name(),new Rect(r.x()+7,r.y()+5,r.w()-14,pokemon?14:17),Math.min(14,Math.max(9,r.w()/9)),Ui.WHITE);
        if(pokemon){
            String stars="★".repeat(Math.max(1,d.level()));
            ui.fit(stars,new Rect(r.x()+7,r.y()+18,r.w()-14,11),Math.min(10,Math.max(7,r.w()/13)),Ui.GOLD);
        }

        int effectH=pokemon?(r.h()<150?30:Math.min(58,r.h()/3)):Math.max(26,Math.min(44,r.h()/3));
        int infoH=pokemon?16:14;
        int artY=r.y()+headerH+3;
        int artBottom=r.bottom()-effectH-infoH-6;
        Rect art=new Rect(r.x()+5,artY,r.w()-10,Math.max(24,artBottom-artY));
        ui.c.fillGradient(art.x(),art.y(),art.right(),art.bottom(),0xFF142B40,0xFF0A1523);
        if(pokemon)PokemonModels.draw(ui,d.species(),d.aspects(),art,key);
        else {var item=switch(d.category()){case "item"->Items.POTION;case "trainer"->Items.WRITABLE_BOOK;case "technique"->Items.BLAZE_POWDER;case "reaction"->Items.SHIELD;case "stadium"->Items.BEACON;default->Items.ENCHANTED_BOOK;};int size=Math.min(art.w()-12,art.h()-8);ui.item(item,new Rect(art.x()+(art.w()-size)/2,art.y()+(art.h()-size)/2,size,size));}

        int infoY=art.bottom()+2;
        ui.fill(new Rect(r.x()+4,infoY,r.w()-8,infoH),0xEE0B1925);
        String tributeTag=pokemon?(d.tributeCount()==0?"FREE":d.tributeCount()+"T"):"";
        String info=pokemon?d.type().toUpperCase()+"  ATK "+d.power()+"  •  "+tributeTag:d.category().toUpperCase();
        ui.fit(info,new Rect(r.x()+8,infoY+3,r.w()-16,infoH-3),Math.min(10,Math.max(7,r.w()/15)),edge);

        Rect rules=new Rect(r.x()+5,infoY+infoH+1,r.w()-10,r.bottom()-(infoY+infoH+1)-4);
        ui.fill(rules,0xEE08131E);
        String rulesText=d.text()==null||d.text().isBlank()?(pokemon?"No effect.":""):d.text();
        ui.paragraph(rulesText,rules.inset(4),Math.max(7,Math.min(10,r.w()/15)),Ui.WHITE);

        if(count>0)ui.text("×"+count,r.right()-28,r.y()+6,11,Ui.WHITE);
        if(!owned)ui.fill(r,0x66061320);if(selected)ui.frame(r.inset(2),Ui.CYAN);
        if(hover){
            String meta=pokemon?" ★"+d.level()+" • "+d.summonRequirement()+" • ATK "+d.power():"";
            ui.tooltip=d.name()+meta+" — "+rulesText+" — Set: "+d.set()+" • Print rarity: "+d.rarity();
        }
    }'''
if old not in s:
    raise SystemExit("CardRenderer draw anchor missing")
s = s.replace(old, new)
p.write_text(s)

import json
p = root / "src/main/resources/data/svarcade_tcg/catalog.json"
data = json.loads(p.read_text())
levels = {
    "charmander":4,"charmeleon":5,"charizard":7,"mega_charizard":8,
    "squirtle":4,"wartortle":5,"blastoise":7,
    "bulbasaur":4,"ivysaur":5,"venusaur":7,
    "pikachu":4,"gastly":3,"haunter":5,"gengar":7,"eevee":4,"onix":5,
    "ancient_mew":8,"shadow_lugia":8
}
for cid, card in data["cards"].items():
    card["level"] = levels.get(cid, 0) if card.get("category") == "pokemon" else 0

texts = {
    "charmander":"Once per turn: inflict 300 damage to your opponent.",
    "charmeleon":"You can evolve this card from Charmander instead of Tribute Summoning it. Once per turn: pay 200 Life; inflict 400 damage to your opponent.",
    "charizard":"You can evolve this card from Charmeleon instead of Tribute Summoning it. Once per turn: pay 500 Life; destroy 1 opposing Pokémon.",
    "mega_charizard":"Extra Deck. Must evolve from Charizard. Once per turn: pay 700 Life; destroy 1 opposing Pokémon.",
    "squirtle":"Once per turn: recover 300 Trainer Life.",
    "wartortle":"You can evolve this card from Squirtle instead of Tribute Summoning it. Once per turn: recover 400 Trainer Life.",
    "blastoise":"You can evolve this card from Wartortle instead of Tribute Summoning it. Once per turn: pay 500 Life; return 1 opposing Pokémon to the hand.",
    "bulbasaur":"Once per turn: 1 allied Pokémon gains 200 ATK.",
    "ivysaur":"You can evolve this card from Bulbasaur instead of Tribute Summoning it. Once per turn: 1 allied Pokémon gains 300 ATK.",
    "venusaur":"You can evolve this card from Ivysaur instead of Tribute Summoning it. Once per turn: recover 600 Trainer Life.",
    "pikachu":"Once per turn: pay 300 Life; inflict 500 damage to your opponent.",
    "gastly":"Once per turn: pay 400 Life; draw 1 card.",
    "haunter":"You can evolve this card from Gastly instead of Tribute Summoning it. Once per turn: pay 300 Life; return 1 opposing Pokémon to the hand.",
    "gengar":"You can evolve this card from Haunter instead of Tribute Summoning it. Once per turn: pay 600 Life; negate the latest Chain effect.",
    "eevee":"If this card is Normal Summoned: recover 200 Trainer Life.",
    "onix":"Once per turn: choose 1 allied Pokémon; protect it from its next destruction.",
    "ancient_mew":"Once per turn: pay 500 Life; draw 1 card.",
    "shadow_lugia":"Once per turn: pay 800 Life; banish 1 opposing Pokémon."
}
for cid, text_value in texts.items():
    data["cards"][cid]["text"] = text_value

def effect(operation, amount=0, speed=1, life=0, target="none", phases=("MAIN1","MAIN2"), once=True):
    return {"operation":operation,"amount":amount,"speed":speed,"lifeCost":life,"target":target,"phases":list(phases),"oncePerTurn":once}
def trigger(cause, operation, amount):
    return {"cause":cause,"zone":"FIELD","relation":"self","effect":effect(operation,amount)}

cards=data["cards"]
cards["charmander"]["effect"]=effect("damage",300)
cards["charmeleon"]["effect"]=effect("damage",400,1,200)
cards["charizard"]["effect"]=effect("destroy",0,1,500,"enemy")
cards["mega_charizard"]["effect"]=effect("destroy",0,1,700,"enemy")
cards["squirtle"]["effect"]=effect("heal",300)
cards["wartortle"]["effect"]=effect("heal",400)
cards["blastoise"]["effect"]=effect("return",0,1,500,"enemy")
cards["bulbasaur"]["effect"]=effect("boost",200,1,0,"ally")
cards["ivysaur"]["effect"]=effect("boost",300,1,0,"ally")
cards["venusaur"]["effect"]=effect("heal",600)
cards["pikachu"]["effect"]=effect("damage",500,1,300)
cards["haunter"]["effect"]=effect("return",0,1,300,"enemy")
cards["onix"]["effect"]=effect("shield",1,1,0,"ally")
cards["ancient_mew"]["effect"]=effect("draw",1,1,500)
# Ancient Mew has an activated effect only; Tribute Summon itself does not open an automatic Chain.
cards["shadow_lugia"]["effect"]=effect("banish",0,1,800,"enemy")
p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

p = root / "src/test/java/vn/svarcade/tcg/EngineTest.java"
s = p.read_text()
insert = '''
    Catalog multiNormal(){var r=base.rules();return new Catalog(new Catalog.Rules(5,60,15,3,5,8000,5,5,4,300,r.rankedLimits(),r.effective()),base.cards(),base.banners(),base.rewards(),base.dealers(),base.starters());}
    @Test void levelsOneToFourNeedNoTributeAndFiveToSixNeedOne(){
        Duel d=new Duel(multiNormal(),List.of("charmander","onix","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(1));main(d);
        String low=token(d,0,"Charmander");act(d,0,"play",low,"");act(d,0,"pass","","");act(d,1,"pass","","");
        String high=token(d,0,"Onix");assertThrows(IllegalArgumentException.class,()->act(d,0,"play",high,""));
        act(d,0,"play",high,low);assertEquals(Duel.Cause.TRIBUTE_SUMMON,d.history().getLast().cause());
        assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Charmander")&&c.zone()==Duel.Zone.DISCARD));
    }
    @Test void levelSevenPlusNeedsTwoTributes(){
        Duel d=new Duel(multiNormal(),List.of("charmander","squirtle","ancient_mew","protect","research"),List.of(),List.of("pikachu","gastly","potion","protect","research"),List.of(),new Random(1));main(d);
        String a=token(d,0,"Charmander"),b=token(d,0,"Squirtle"),boss=token(d,0,"Ancient Mew");
        act(d,0,"play",a,"");act(d,0,"pass","","");act(d,1,"pass","","");
        act(d,0,"play",b,"");act(d,0,"pass","","");act(d,1,"pass","","");
        assertThrows(IllegalArgumentException.class,()->act(d,0,"play",boss,a));
        act(d,0,"play",boss,a+","+b);assertEquals(Duel.Cause.TRIBUTE_SUMMON,d.history().getLast().cause());
    }
'''
if "levelsOneToFourNeedNoTributeAndFiveToSixNeedOne" in s:
    raise SystemExit("Engine tribute tests already present")
idx = s.rfind("\n}")
if idx < 0:
    raise SystemExit("EngineTest closing brace missing")
s = s[:idx] + insert + s[idx:]
p.write_text(s)

# NPC duel AI must understand 0, 1 and 2-material summon attempts.
p = root / "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java"
s = p.read_text()
old = '''        if(v.open()&&v.turnPlayer()==1&&(v.phase().equals("MAIN1")||v.phase().equals("MAIN2")))for(var c:v.cards())if(c.controller()==1&&c.zone()==Duel.Zone.HAND&&c.category().equals("pokemon")){
            actions.add(new Duel.Action("play",c.token(),""));for(var material:v.cards())if(material.controller()==1&&material.zone()==Duel.Zone.FIELD)actions.add(new Duel.Action("play",c.token(),material.token()));
        }'''
new = '''        if(v.open()&&v.turnPlayer()==1&&(v.phase().equals("MAIN1")||v.phase().equals("MAIN2")))for(var c:v.cards())if(c.controller()==1&&c.zone()==Duel.Zone.HAND&&c.category().equals("pokemon")){
            actions.add(new Duel.Action("play",c.token(),""));
            var field=v.cards().stream().filter(x->x.controller()==1&&x.zone()==Duel.Zone.FIELD).toList();
            for(var material:field)actions.add(new Duel.Action("play",c.token(),material.token()));
            for(int i=0;i<field.size();i++)for(int j=i+1;j<field.size();j++)actions.add(new Duel.Action("play",c.token(),field.get(i).token()+","+field.get(j).token()));
        }'''
if old not in s:
    raise SystemExit("TcgMod bot summon anchor missing")
s = s.replace(old, new)
p.write_text(s)

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
    @Override public boolean shouldCloseOnEsc(){return !duelActive();}
    @Override public void close(){if(duelActive())return;duelPage.closeScene();super.close();}
    @Override public boolean shouldPause(){return false;}
}'''
if old not in s:
    raise SystemExit("CardWorldsScreen close anchor missing")
s = s.replace(old, new)

old = '@Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,scroll+(v<0?1:-1));return true;}'
new = '@Override public boolean mouseScrolled(double x,double y,double h,double v){if(duelActive()&&duelPage.freeLookScroll(v))return true;scroll=Math.max(0,scroll+(v<0?1:-1));return true;}'
if old not in s:
    raise SystemExit("CardWorldsScreen existing mouseScrolled anchor missing")
s = s.replace(old, new)
p.write_text(s)


p = root / "src/main/java/vn/svarcade/tcg/client/screens/CollectionScreen.java"
s = p.read_text()
old = 'u.text(card.rarity()+" · "+card.type()+" · "+a.count(card.id())+" owned",r.x()+16,y+30,14,Ui.GOLD);'
new = 'u.text(card.category().equals("pokemon")?"★"+card.level()+" · "+card.type().toUpperCase()+" · "+card.summonRequirement()+" · "+a.count(card.id())+" owned":card.category().toUpperCase()+" · "+a.count(card.id())+" owned",r.x()+16,y+30,14,Ui.GOLD);'
if old not in s:
    raise SystemExit("Collection inspector metadata anchor missing")
s = s.replace(old, new)
old = 'u.text(card.set(),r.x()+16,r.bottom()-119,13,Ui.MUTED);'
new = 'u.text("Set: "+card.set()+" · Print rarity: "+card.rarity(),r.x()+16,r.bottom()-119,13,Ui.MUTED);'
if old not in s:
    raise SystemExit("Collection inspector set anchor missing")
s = s.replace(old, new)
p.write_text(s)

p = root / "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java"
s = p.read_text()
# Exercise the production root command, not only the legacy /tcg alias.
s = s.replace('sendChatCommand("tcg")', 'sendChatCommand("cardworlds")')
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


# PvE/PvP split, bot difficulty, optional economy payout, and production command layer.
import json

# Adventure/MiniMessage is bundled; Text Placeholder API remains an optional compile/runtime hook.
p = root / "build.gradle"
s = p.read_text()
if "https://maven.nucleoid.xyz" not in s:
    s = s.replace("mavenCentral()", "mavenCentral()\n    maven { url 'https://maven.nucleoid.xyz' }", 1)
if "adventure-platform-fabric:5.14.2" not in s:
    s = s.replace("dependencies {", """dependencies {
    modImplementation include('net.kyori:adventure-platform-fabric:5.14.2')
    modCompileOnly 'eu.pb4:placeholder-api:2.4.2+1.21'""", 1)
p.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/economy/CardStore.java"
s = p.read_text()
anchor = '    private static List<String> concat(List<String>a,List<String>b){List<String> out=new ArrayList<>(a);out.addAll(b);return out;}\n'
if anchor not in s:
    raise SystemExit("CardStore admin grant anchor missing")
admin_methods = '''    public synchronized boolean hasProfile(String owner){
        try{return scalar("SELECT COUNT(*) FROM profiles WHERE owner=?",owner)>0;}
        catch(SQLException ex){throw new IllegalStateException(ex);}
    }
    public void adminGrantCard(String owner,String cardId,int amount,String finish){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");check(amount>=1&&amount<=1000,"INVALID_AMOUNT");
        catalog.card(cardId);check(finish!=null&&!finish.isBlank(),"INVALID_FINISH");
        for(int i=0;i<amount;i++)mint(owner,cardId,finish,"ADMIN_GRANT");return null;
    });}
    public void adminGrantCoins(String owner,long amount){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");check(amount>0&&amount<=1_000_000_000L,"INVALID_AMOUNT");
        credit(owner,amount);return null;
    });}
    public void adminGrantDeck(String owner,String template,String deckName){tx(()->{
        check(hasProfile(owner),"PROFILE_MISSING");
        List<String> ids=catalog.starters().get(template);check(ids!=null,"INVALID_TEMPLATE");
        check(deckName!=null&&!deckName.isBlank()&&deckName.length()<=64,"INVALID_DECK_NAME");
        List<String> serials=new ArrayList<>();
        for(String id:ids)serials.add(mint(owner,id,"Normal","ADMIN_DECK:"+template));
        update("INSERT INTO decks(owner,name,main,extra) VALUES(?,?,?,?) ON CONFLICT(owner,name) DO UPDATE SET main=excluded.main,extra=excluded.extra",
            owner,deckName,gson.toJson(serials),"[]");
        update("INSERT INTO deck_formats(owner,name,format) VALUES(?,?,?) ON CONFLICT(owner,name) DO UPDATE SET format=excluded.format",
            owner,deckName,Format.CASUAL.name());
        return null;
    });}
'''
s = s.replace(anchor, admin_methods + anchor, 1)
p.write_text(s)

p = root / "src/main/java/vn/svarcade/tcg/fabric/TcgMod.java"
s = p.read_text()

old = 'Duel duel;boolean ranked,npc;long activity=System.currentTimeMillis();'
new = 'Duel duel;boolean ranked,npc;String botDifficulty="NONE";long activity=System.currentTimeMillis();'
if old not in s:
    raise SystemExit("TcgMod botDifficulty Match anchor missing")
s = s.replace(old, new, 1)

field_pattern = re.compile(r'(\bprivate\s+(?:vn\.svarcade\.tcg\.economy\.)?CardStore\s+store\s*;)')
field_match = field_pattern.search(s)
if not field_match:
    raise SystemExit("TcgMod service field anchor missing")
field_insert = '''
    private vn.svarcade.tcg.integration.EconomyRewards economyRewards;
    private MessageService messages;'''
s = s[:field_match.end()] + field_insert + s[field_match.end():]

# Register optional Card Worlds placeholders once; handler values remain live against current runtime state.
payload_anchor = '        PayloadTypeRegistry.playC2S().register(TcgPackets.Input.ID,TcgPackets.Input.CODEC);\n'
if payload_anchor not in s:
    raise SystemExit("TcgMod placeholder registration anchor missing")
s = s.replace(payload_anchor, payload_anchor + '''        if(FabricLoader.getInstance().isModLoaded("placeholder-api"))PlaceholderSupport.register(this);
''', 1)

old_command = '''        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("tcg").executes(ctx->{openRequests.add(ctx.getSource().getPlayerOrThrow().getUuid());send(ctx.getSource().getPlayerOrThrow(),"",0);return 1;})
            .then(literal("starter").executes(ctx->{handle(ctx.getSource().getPlayerOrThrow(),new TcgPackets.Request("starter",List.of(),0));return 1;}))));'''
if old_command not in s:
    raise SystemExit("TcgMod legacy command registration anchor missing")
s = s.replace(old_command, '        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->CardWorldsCommands.register(dispatcher,this));', 1)

npc_old = '''                case "npc" -> {
                    check(!matches.containsKey(player),"Finish your current duel first.");Match m=new Match();m.a=player;m.b=UUID.randomUUID();m.npc=true;
                    try{var da=store.lockDeck(owner,a.isEmpty()?"Starter":a.getFirst(),false,m.id);m.duel=new Duel(catalog,da.get(0),da.get(1),catalog.starters().get("crossroads"),List.of(),rng);}catch(Exception ex){store.unlockDuel(m.id);throw ex;}
                    matches.put(player,m);notice="The Pewter challenger is ready.";
                }'''
npc_new = '''                case "npc","pve" -> {
                    check(!matches.containsKey(player),"Finish your current duel first.");
                    String difficulty=r.action().equals("npc")?"NORMAL":(a.size()>=2?a.get(1):"NORMAL").toUpperCase(Locale.ROOT);
                    check(Set.of("EASY","NORMAL","HARD").contains(difficulty),"Bot difficulty must be EASY, NORMAL or HARD.");
                    Match m=new Match();m.a=player;m.b=UUID.randomUUID();m.npc=true;m.botDifficulty=difficulty;
                    String deckName=a.isEmpty()||a.getFirst().isBlank()?"Starter":a.getFirst();
                    try{var da=store.lockDeck(owner,deckName,false,m.id);m.duel=new Duel(catalog,da.get(0),da.get(1),catalog.starters().get("crossroads"),List.of(),rng);}catch(Exception ex){store.unlockDuel(m.id);throw ex;}
                    matches.put(player,m);notice=difficulty;
                }'''
if npc_old not in s:
    raise SystemExit("TcgMod npc case anchor missing")
s = s.replace(npc_old, npc_new, 1)

bot_pattern = re.compile(r'(?s)    private void bot\(Match m\)\s*\{.*?\n    private void broadcast')
bot_match = bot_pattern.search(s)
if not bot_match:
    raise SystemExit("TcgMod bot method anchor missing")
bot_impl = '''    private void bot(Match m){
        Duel.View v=m.duel.view(1);
        List<Duel.Action> actions=new ArrayList<>();
        List<Duel.VisibleCard> mine=v.cards().stream().filter(c->c.controller()==1).toList();
        List<Duel.VisibleCard> field=mine.stream().filter(c->c.zone()==Duel.Zone.FIELD).toList();
        List<Duel.VisibleCard> enemy=v.cards().stream().filter(c->c.controller()==0&&c.zone()==Duel.Zone.FIELD).toList();

        if(v.open()&&v.turnPlayer()==1&&(v.phase().equals("MAIN1")||v.phase().equals("MAIN2"))){
            for(var c:mine)if((c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.EXTRA)&&c.category().equals("pokemon")){
                actions.add(new Duel.Action("play",c.token(),""));
                for(var material:field)actions.add(new Duel.Action("play",c.token(),material.token()));
                for(int i=0;i<field.size();i++)for(int j=i+1;j<field.size();j++)
                    actions.add(new Duel.Action("play",c.token(),field.get(i).token()+","+field.get(j).token()));
            }
        }

        if(v.open()&&v.turnPlayer()==1&&v.phase().equals("BATTLE")){
            for(var attacker:field){
                actions.add(new Duel.Action("attack",attacker.token(),""));
                for(var target:enemy)actions.add(new Duel.Action("attack",attacker.token(),target.token()));
            }
        }

        for(var c:mine)if(c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.FIELD){
            Catalog.Card d=botDefinition(c);
            if(d==null||d.effect()==null)continue;
            String target=d.effect().target();
            if(target.equals("none"))actions.add(new Duel.Action("activate",c.token(),""));
            else if(target.equals("chain")&&!v.chain().isEmpty())actions.add(new Duel.Action("activate",c.token(),Integer.toString(v.chain().size())));
            else if(target.equals("enemy"))for(var t:enemy)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("ally"))for(var t:field)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("grave"))for(var t:mine)if(t.zone()==Duel.Zone.DISCARD)actions.add(new Duel.Action("activate",c.token(),t.token()));
        }

        if(v.open()&&v.turnPlayer()==1)actions.add(new Duel.Action("next","",""));
        actions.add(new Duel.Action("pass","",""));

        if("EASY".equals(m.botDifficulty))Collections.shuffle(actions,rng);
        else{
            boolean hard="HARD".equals(m.botDifficulty);
            actions.sort(Comparator.comparingInt((Duel.Action action)->botScore(v,action,hard)).reversed());
            if(!hard&&actions.size()>2&&rng.nextInt(100)<22)Collections.swap(actions,0,1+rng.nextInt(Math.min(3,actions.size()-1)));
        }

        for(var action:actions)try{m.duel.act(1,action,m.duel.revision());return;}catch(IllegalArgumentException ignored){}
    }

    private Catalog.Card botDefinition(Duel.VisibleCard card){
        return catalog.cards().values().stream().filter(d->d.name().equals(card.name())).findFirst().orElse(null);
    }

    private int botScore(Duel.View v,Duel.Action action,boolean hard){
        if(action.kind().equals("pass"))return -100000;
        if(action.kind().equals("next"))return -50000;
        Duel.VisibleCard source=v.cards().stream().filter(c->c.token().equals(action.card())).findFirst().orElse(null);
        if(source==null)return -90000;
        Catalog.Card def=botDefinition(source);
        if(action.kind().equals("play")){
            int score=1200+source.power()+(def==null?0:def.level()*90);
            if(!action.target().isBlank())for(String token:action.target().split(",")){
                Duel.VisibleCard tribute=v.cards().stream().filter(c->c.token().equals(token)).findFirst().orElse(null);
                if(tribute!=null)score-=hard?tribute.power()/2:tribute.power()/3;
            }
            return score;
        }
        if(action.kind().equals("attack")){
            if(action.target().isBlank())return 2600+source.power();
            Duel.VisibleCard target=v.cards().stream().filter(c->c.token().equals(action.target())).findFirst().orElse(null);
            if(target==null)return 500;
            int trade=source.power()-target.power();
            return hard?((trade>=0?3600:-1800)+target.power()+trade):((trade>=0?2500:-500)+target.power()/2);
        }
        if(action.kind().equals("activate")&&def!=null&&def.effect()!=null){
            int base=switch(def.effect().operation()){
                case "negate" -> 5200;
                case "banish" -> 4900;
                case "destroy" -> 4700;
                case "return" -> 4300;
                case "damage" -> 3500+def.effect().amount();
                case "draw" -> 3300+def.effect().amount()*300;
                case "heal" -> 2100+def.effect().amount()/2;
                case "boost" -> 2300+def.effect().amount();
                case "shield" -> 2400;
                default -> 1600;
            };
            if(hard)base-=def.effect().lifeCost();
            if(!action.target().isBlank()){
                Duel.VisibleCard target=v.cards().stream().filter(c->c.token().equals(action.target())).findFirst().orElse(null);
                if(target!=null&&target.controller()==0)base+=hard?target.power()/2:target.power()/4;
            }
            return base;
        }
        return 0;
    }

    private void broadcast'''
s = s[:bot_match.start()] + bot_impl + s[bot_match.end():]

reward_old = '        if(m.npc&&winning==0)store.npcVictory(m.a.toString(),"pewter_victory",m.id,Instant.now().getEpochSecond());'
reward_new = '''        if(m.npc&&winning==0){
            ServerPlayerEntity botWinner=server.getPlayerManager().getPlayer(m.a);
            if("HARD".equals(m.botDifficulty)){
                store.npcVictory(m.a.toString(),"pewter_victory",m.id,Instant.now().getEpochSecond());
                if(botWinner!=null){
                    if(economyRewards==null)economyRewards=new vn.svarcade.tcg.integration.EconomyRewards();
                    var payout=economyRewards.payHardWin(botWinner,m.id);
                    send(botWinner,payout.message(),0);
                }
            }
        }'''
if reward_old not in s:
    raise SystemExit("TcgMod NPC reward anchor missing")
s = s.replace(reward_old, reward_new, 1)

method_anchor = '    private static void check(boolean v,String m){if(!v)throw new IllegalArgumentException(m);}\n}'
if method_anchor not in s:
    raise SystemExit("TcgMod command service method anchor missing")
command_methods = '''    MessageService commandMessages(MinecraftServer server){
        if(messages==null)messages=new MessageService(server);
        return messages;
    }
    Catalog commandCatalog(){return catalog;}
    CardStore commandStore(){return store;}
    boolean commandHasActiveDuels(){return !matches.isEmpty();}
    void commandOpen(ServerPlayerEntity player){openRequests.add(player.getUuid());send(player,"",0);}
    synchronized void commandReload(MinecraftServer server)throws Exception{
        commandMessages(server).reload();
        if(!matches.isEmpty())throw new IllegalStateException("ACTIVE_DUELS");
        Path config=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg");
        Files.createDirectories(config);
        Path file=config.resolve("catalog.json");
        Catalog next=Catalog.load(file);
        Path db=server.getSavePath(WorldSavePath.ROOT).resolve("svarcade-tcg/cards.db");
        Catalog previousCatalog=catalog;
        CardStore previousStore=store;
        if(previousStore!=null)previousStore.close();
        try{
            store=new CardStore(db,next,rng);
            catalog=next;
        }catch(Exception ex){
            catalog=previousCatalog;
            store=previousCatalog==null?null:new CardStore(db,previousCatalog,rng);
            throw ex;
        }
        economyRewards=new vn.svarcade.tcg.integration.EconomyRewards();
    }
    long placeholderCoins(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).coins();
    }
    int placeholderRating(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).rating();
    }
    int placeholderCards(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.profile(player.getUuidAsString()).owned();
    }
    int placeholderDecks(ServerPlayerEntity player){
        return store==null||!store.hasProfile(player.getUuidAsString())?0:store.deckNames(player.getUuidAsString()).size();
    }
    String placeholderDuelState(ServerPlayerEntity player){
        Match match=matches.get(player.getUuid());
        return match==null?"idle":match.npc?"pve":"pvp";
    }
    String placeholderBotDifficulty(ServerPlayerEntity player){
        Match match=matches.get(player.getUuid());
        return match==null||!match.npc?"none":match.botDifficulty.toLowerCase(Locale.ROOT);
    }
'''
s = s.replace(method_anchor, command_methods + method_anchor, 1)

stop_anchor = 'matches.clear();challenges.clear();rateLimit.clear();});'
if stop_anchor in s:
    s = s.replace(stop_anchor, 'matches.clear();challenges.clear();rateLimit.clear();messages=null;});', 1)

p.write_text(s)

# Suggested integrations remain optional.
p = root / "src/main/resources/fabric.mod.json"
meta = json.loads(p.read_text())
suggests = meta.setdefault("suggests", {})
for mod_id in ("placeholder-api","beconomy","cobbledollars","impactor"):
    suggests.setdefault(mod_id, "*")
p.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n")

# Runtime QA exercises explicit HARD PvE.
p = root / "src/qa/java/vn/svarcade/tcg/qa/VisualRun.java"
s = p.read_text()
if 'a.send("npc",a.deckName);' in s:
    s = s.replace('a.send("npc",a.deckName);', 'a.send("pve",a.deckName,"HARD");', 1)
p.write_text(s)

# Admin-grant persistence regression tests.
p = root / "src/test/java/vn/svarcade/tcg/EconomyTest.java"
s = p.read_text()
if "adminGrantCommandsPersistCardsCoinsAndDecks" not in s:
    insert = '''
    @Test void adminGrantCommandsPersistCardsCoinsAndDecks(){
        store.createProfile("admin-target","crossroads");
        long before=store.profile("admin-target").coins();
        int cardsBefore=store.profile("admin-target").owned();
        store.adminGrantCard("admin-target","pikachu",2,"Holo");
        store.adminGrantCoins("admin-target",250);
        store.adminGrantDeck("admin-target","crossroads","Granted");
        assertTrue(store.profile("admin-target").coins()>=before+250);
        assertTrue(store.profile("admin-target").owned()>cardsBefore+1);
        assertTrue(store.deckNames("admin-target").contains("Granted"));
        assertTrue(store.completion("admin-target").getOrDefault("pikachu",0)>=2);
    }
'''
    idx=s.rfind("\n}")
    if idx<0: raise SystemExit("EconomyTest closing brace missing")
    s=s[:idx]+insert+s[idx:]
p.write_text(s)


# Apply the verified Duel Realm / coliseum / spectator / spell-trap / summon bundle last.
import runpy
runpy.run_path(str(root / "source-overrides" / "duel-realm" / "apply_bundle.py"), run_name="__main__")
