from pathlib import Path
import json, re, runpy

ROOT=Path.cwd()
MAIN=ROOT/'src/main'
JAVA=MAIN/'java/vn/svarcade/tcg'
RES=MAIN/'resources'

def replace_once(path,old,new,label):
    s=path.read_text()
    if old not in s: raise SystemExit('YGO fidelity anchor missing: '+label)
    path.write_text(s.replace(old,new,1))

def patch(path,fn):
    s=path.read_text(); n=fn(s)
    if n==s: raise SystemExit('YGO fidelity patch made no change: '+str(path))
    path.write_text(n)

# ---------------------------------------------------------------------------
# 1) Real ATK / DEF card schema, backwards compatible with all old authored data.
# ---------------------------------------------------------------------------
catalog=JAVA/'data/Catalog.java'
s=catalog.read_text()
card_start=s.find('    public record Card(')
tribute=s.find('        public int tributeCount() {',card_start)
if card_start<0 or tribute<0: raise SystemExit('Catalog.Card structural anchor missing')
new='''    public record Card(String id, String name, String category, String species, List<String> aspects,
                       String type, String family, String evolvesFrom, boolean extra, int level, int power, int defense,
                       String text, String set, String rarity, List<String> sources, Effect effect,
                       List<Trigger> triggers,List<Modifier> modifiers) {
        public Card {
            if(category.equals("pokemon") && defense<=0) defense=defaultDefense(id,type,level,power);
        }
        /** Binary/source compatibility: legacy power is ATK. Old JSON/constructors receive a deterministic DEF. */
        public Card(String id,String name,String category,String species,List<String> aspects,String type,String family,String evolvesFrom,
                    boolean extra,int level,int power,String text,String set,String rarity,List<String> sources,Effect effect,List<Trigger> triggers,List<Modifier> modifiers) {
            this(id,name,category,species,aspects,type,family,evolvesFrom,extra,level,power,
                category.equals("pokemon")?defaultDefense(id,type,level,power):0,text,set,rarity,sources,effect,triggers,modifiers);
        }
        public int atk(){return power;}
        private static int defaultDefense(String id,String type,int level,int atk) {
            int bias=switch(type==null?"normal":type){case "steel"->320;case "rock"->280;case "ground"->220;case "water"->150;
                case "grass","ice","poison","dragon"->100;case "bug","fairy","fighting","ghost"->60;case "dark"->20;
                case "fire","electric","flying"->-80;default->0;};
            int identity=(Math.floorMod((id==null?"":id).hashCode(),9)-4)*35;
            return Math.clamp((int)Math.round(atk*.78)+bias+(level-4)*35+identity,500,3500);
        }
'''
s=s[:card_start]+new+s[tribute:]
s=s.replace('if(!e.getKey().equals(c.id) || c.power < 0 || c.sources.isEmpty())','if(!e.getKey().equals(c.id) || c.power < 0 || c.defense < 0 || c.sources.isEmpty())',1)
s=s.replace('if(c.category.equals("pokemon") && (c.level < 1 || c.level > 12))','if(c.category.equals("pokemon") && (c.level < 1 || c.level > 12 || c.defense<=0))',1)
catalog.write_text(s)

# Explicitly migrate stored/static card JSON. Dynamic Cobblemon/fakemon cards use the compatibility constructor.
def compute_def(card_id,c):
    if c.get('category')!='pokemon': return 0
    atk=int(c.get('power') or 0); level=int(c.get('level') or 4); typ=str(c.get('type') or 'normal')
    bias={'steel':320,'rock':280,'ground':220,'water':150,'grass':100,'ice':100,'poison':100,'dragon':100,
          'bug':60,'fairy':60,'fighting':60,'ghost':60,'dark':20,'fire':-80,'electric':-80,'flying':-80}.get(typ,0)
    digest=int.from_bytes(__import__('hashlib').sha256(card_id.encode()).digest()[:2],'big')
    identity=(digest%9-4)*35
    return max(500,min(3500,round(atk*.78)+bias+(level-4)*35+identity))
for name in ('catalog.json','deep_effects.json'):
    p=RES/'data/svarcade_tcg'/name
    if not p.exists(): continue
    j=json.loads(p.read_text())
    cards=j.get('cards',{})
    for card_id,c in cards.items():
        if isinstance(c,dict) and c.get('category')=='pokemon' and int(c.get('defense') or 0)<=0:
            c['defense']=compute_def(card_id,c)
    p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')

# ---------------------------------------------------------------------------
# 2) Effect vocabulary: ATK/DEF are independently targetable.
# ---------------------------------------------------------------------------
effect_spec=JAVA/'data/EffectSpec.java'
s=effect_spec.read_text()
s=s.replace('"DAMAGE_LP","HEAL_LP","MODIFY_POWER","SET_POWER","SWAP_POWER","DRAW"', '"DAMAGE_LP","HEAL_LP","MODIFY_POWER","MODIFY_ATK","MODIFY_DEF","SET_POWER","SET_ATK","SET_DEF","SWAP_POWER","SWAP_ATK_DEF","DRAW"',1)
s=s.replace('"TYPE","TAG","LEVEL_AT_LEAST","POWER_AT_LEAST","POSITION"', '"TYPE","TAG","LEVEL_AT_LEAST","POWER_AT_LEAST","ATK_AT_LEAST","DEF_AT_LEAST","POSITION"',1)
effect_spec.write_text(s)

# ---------------------------------------------------------------------------
# 3) Duel engine: separate ATK/DEF, real Defense battle calculation,
#    turn-1 Battle/Main2 skip, MP1->End, and a distinct Damage Step response gate.
# ---------------------------------------------------------------------------
duel=JAVA/'duel/Duel.java'
s=duel.read_text()
old='public enum BattlePosition { ATTACK, DEFENSE, FACE_DOWN_DEFENSE }'
s=s.replace(old,old+'\n    private enum BattleWindow { NONE, DECLARE, DAMAGE }',1)

old=re.search(r'    public record VisibleCard\(String token, String name, String species, List<String> aspects, String type, String category, int power, String text, Zone zone, int controller, String position,Catalog\.Effect effect,Map<String,Integer> counters\) \{[^\n]+\}',s)
if not old: raise SystemExit('VisibleCard anchor missing')
new='''    public record VisibleCard(String token, String name, String species, List<String> aspects, String type, String category, int power, int defense, String text, Zone zone, int controller, String position,Catalog.Effect effect,Map<String,Integer> counters) {
        public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position,Catalog.Effect effect){this(token,name,species,aspects,type,category,power,power,text,zone,controller,position,effect,Map.of());}
        public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position){this(token,name,species,aspects,type,category,power,power,text,zone,controller,position,null,Map.of());}
    }'''
s=s[:old.start()]+new+s[old.end():]

s=s.replace('final String token; final Catalog.Card card; final int owner; int controller; Zone zone; int boost, shield, generation; boolean attacked;\n        int fixedPower=-1,extraAttacks,lookedBy=-1,permanentBoost;',
'''final String token; final Catalog.Card card; final int owner; int controller; Zone zone; int boost, defBoost, shield, generation; boolean attacked;
        int fixedPower=-1,fixedDefense=-1,extraAttacks,lookedBy=-1,permanentBoost,permanentDefBoost;''',1)
s=s.replace('private boolean open=true,advance=false;\n    private String attacker,target;',
'''private boolean open=true,advance=false,endRequested=false;
    private BattleWindow battleWindow=BattleWindow.NONE;
    private String attacker,target;''',1)

s=s.replace('case "next" -> {require(open&&actor==turnPlayer,"Finish the response window first.");advance=true;open=false;passes=1;priority=1-actor;}',
'''case "next" -> {require(open&&actor==turnPlayer,"Finish the response window first.");advance=true;open=false;passes=1;priority=1-actor;}
            case "end" -> {require(open&&actor==turnPlayer&&(phase==Phase.MAIN1||phase==Phase.MAIN2),"You can end the turn only from a Main Phase.");endRequested=true;advance=true;open=false;passes=1;priority=1-actor;}''',1)

# Preserve ATK/DEF state across evolutions / staged transformations.
s=s.replace('p.boost=previous.boost;p.shield=previous.shield;p.attacked=previous.attacked;',
            'p.boost=previous.boost;p.defBoost=previous.defBoost;p.fixedPower=previous.fixedPower;p.fixedDefense=previous.fixedDefense;p.permanentBoost=previous.permanentBoost;p.permanentDefBoost=previous.permanentDefBoost;p.shield=previous.shield;p.attacked=previous.attacked;',1)
s=s.replace('p.boost=candidate.boost;p.shield=candidate.shield;p.attacked=candidate.attacked;',
            'p.boost=candidate.boost;p.defBoost=candidate.defBoost;p.fixedPower=candidate.fixedPower;p.fixedDefense=candidate.fixedDefense;p.permanentBoost=candidate.permanentBoost;p.permanentDefBoost=candidate.permanentDefBoost;p.shield=candidate.shield;p.attacked=candidate.attacked;',1)
s=s.replace('replacement.boost=current.boost;replacement.shield=current.shield;replacement.attacked=current.attacked;',
            'replacement.boost=current.boost;replacement.defBoost=current.defBoost;replacement.fixedPower=current.fixedPower;replacement.fixedDefense=current.fixedDefense;replacement.permanentBoost=current.permanentBoost;replacement.permanentDefBoost=current.permanentDefBoost;replacement.shield=current.shield;replacement.attacked=current.attacked;',1)

# Damage Step activation restriction.
needle='require(e.phases().contains(phase.name()),"This effect cannot be used in this phase.");\n        if(e.speed()==1) mainAction(actor);'
if needle not in s: raise SystemExit('activate phase anchor missing')
s=s.replace(needle,'require(e.phases().contains(phase.name()),"This effect cannot be used in this phase.");\n        if(phase==Phase.BATTLE&&battleWindow==BattleWindow.DAMAGE)require(damageStepLegal(e),"This effect cannot be activated during the Damage Step.");\n        if(e.speed()==1) mainAction(actor);',1)

# Attack declaration enters its own response window; Damage Step opens only after both players pass declaration.
s=s.replace('if(p.attacked)p.extraAttacks--;p.attacked=true;attacker=token;target=selected;',
            'if(p.attacked)p.extraAttacks--;p.attacked=true;attacker=token;target=selected;battleWindow=BattleWindow.DECLARE;',1)
s=s.replace('if(attacker!=null){battle();attacker=null;target=null;if(winner<0)window();return;}',
'''if(attacker!=null){
            if(battleWindow==BattleWindow.DECLARE){battleWindow=BattleWindow.DAMAGE;note("Damage Step.");window();return;}
            battle();attacker=null;target=null;battleWindow=BattleWindow.NONE;if(winner<0)window();return;
        }''',1)

# Real DEF in battle and expose helpers.
s=s.replace('int difference=attack-power(d);','int difference=attack-defense(d);',1)
power_line='private int power(Piece p) {if(p.zone!=Zone.FIELD)return p.card.power();int result=p.card.power()+p.boost;for(Piece source:pieces.values())if(source.controller==p.controller&&source.card.modifiers()!=null)for(var m:source.card.modifiers())if(source.zone.name().equals(m.zone())&&(m.affectedType().equals("any")||m.affectedType().equals(p.card.type())))result+=m.power();return compositePower(p,result);}'
if power_line not in s: raise SystemExit('power() anchor missing')
s=s.replace(power_line,power_line+'''
    private int defense(Piece p) {if(p.zone!=Zone.FIELD)return p.card.defense();return compositeDefense(p,p.card.defense()+p.defBoost);}
    private boolean damageStepLegal(Catalog.Effect effect) {
        if(effect==null)return false;
        if(effect.speed()>=3)return true;
        if(effect.spec()==null)return Set.of("negate_effect","negate_activation","boost","shield").contains(effect.operation());
        return damageStepOperations(effect.spec().operations())&&vn.svarcade.tcg.data.EffectSpec.list(effect.spec().stages()).stream().allMatch(stage->damageStepOperations(stage.effect().spec().operations()));
    }
    private boolean damageStepOperations(List<vn.svarcade.tcg.data.EffectSpec.Operation> operations) {
        Set<String> legal=Set.of("MODIFY_POWER","MODIFY_ATK","MODIFY_DEF","SET_POWER","SET_ATK","SET_DEF","SWAP_ATK_DEF","NEGATE_EFFECT","NEGATE_ACTIVATION","PREVENT_DESTROY","REMOVE_STATUS","IF");
        for(var op:vn.svarcade.tcg.data.EffectSpec.list(operations)){
            if(!legal.contains(op.type()))return false;
            if(!damageStepOperations(op.children())||!damageStepOperations(op.otherwise()))return false;
        }
        return true;
    }''',1)

# Correct phase transition.
old='''        if(phase==Phase.END){turn++;turnPlayer=1-turnPlayer;phase=Phase.DRAW;normal[turnPlayer]=0;used.clear();pieces.values().forEach(p->{p.attacked=false;p.boost=p.permanentBoost+expiries.stream().filter(e->e.token().equals(p.token)&&e.key().equals("BOOST")&&e.endTurn()>=turn).mapToInt(Expiry::value).sum();});tickAdvancedSummons(turnPlayer);draw(turnPlayer);}
        else phase=Phase.values()[phase.ordinal()+1];'''
new='''        if(phase==Phase.END){turn++;turnPlayer=1-turnPlayer;phase=Phase.DRAW;normal[turnPlayer]=0;used.clear();endRequested=false;battleWindow=BattleWindow.NONE;pieces.values().forEach(p->{p.attacked=false;p.boost=p.permanentBoost+expiries.stream().filter(e->e.token().equals(p.token)&&e.key().equals("BOOST")&&e.endTurn()>=turn).mapToInt(Expiry::value).sum();p.defBoost=p.permanentDefBoost+expiries.stream().filter(e->e.token().equals(p.token)&&e.key().equals("DEF_BOOST")&&e.endTurn()>=turn).mapToInt(Expiry::value).sum();});tickAdvancedSummons(turnPlayer);draw(turnPlayer);}
        else if(phase==Phase.MAIN1&&(turn==1||endRequested)){phase=Phase.END;endRequested=false;}
        else phase=Phase.values()[phase.ordinal()+1];'''
if old not in s: raise SystemExit('nextPhase anchor missing')
s=s.replace(old,new,1)

# Reset both stats when a monster leaves the field.
s=s.replace('if(to!=Zone.FIELD){p.boost=0;p.shield=0;p.attacked=false;p.position=BattlePosition.ATTACK;p.summonedTurn=-1;p.positionTurn=-1;p.flags.clear();p.fixedPower=-1;p.extraAttacks=0;p.permanentBoost=0;',
'''if(to!=Zone.FIELD){p.boost=0;p.defBoost=0;p.shield=0;p.attacked=false;p.position=BattlePosition.ATTACK;p.summonedTurn=-1;p.positionTurn=-1;p.flags.clear();p.fixedPower=-1;p.fixedDefense=-1;p.extraAttacks=0;p.permanentBoost=0;p.permanentDefBoost=0;''',1)

# Visible state carries both stats; hidden Set monsters leak neither.
s=s.replace('hiddenCategory,0,"",p.zone,p.controller,p.position.name())','hiddenCategory,0,0,"",p.zone,p.controller,p.position.name())',1)
s=s.replace('p.card.type(),category,power(p),p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p),Map.copyOf(p.counters))',
            'p.card.type(),category,power(p),defense(p),p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p),Map.copyOf(p.counters))',1)

# Conditions.
s=s.replace('case "POWER_AT_LEAST" -> targets.stream().anyMatch(p->(powerCondition?p.card.power()+p.boost:power(p))>=c.amount());',
'''case "POWER_AT_LEAST","ATK_AT_LEAST" -> targets.stream().anyMatch(p->(powerCondition?p.card.power()+p.boost:power(p))>=c.amount());
            case "DEF_AT_LEAST" -> targets.stream().anyMatch(p->defense(p)>=c.amount());''',1)

# Continuous DEF calculation.
anchor='''    private int compositePower(Piece p,int value) {
        if(powerCondition)return Math.max(0,value);powerCondition=true;try {
        if(p.fixedPower>=0)value=p.fixedPower;
        for(var entry:continuous.entrySet()) {
            Piece source=pieces.get(entry.getKey());if(source==null)continue;
            for(var op:entry.getValue().operations())if(op.type().equals("MODIFY_POWER")
                &&condition(op.condition(),source,p.token,source.controller)&&select(op.target(),source,p.token,source.controller,op.filter()!=null?op.filter():entry.getValue().targets()==null?null:entry.getValue().targets().filter()).contains(p))value+=op.amount();
        }
        return Math.max(0,value);
        }finally{powerCondition=false;}
    }'''
if anchor not in s: raise SystemExit('compositePower anchor missing')
s=s.replace(anchor,anchor.replace('op.type().equals("MODIFY_POWER")','Set.of("MODIFY_POWER","MODIFY_ATK").contains(op.type())')+'''
    private int compositeDefense(Piece p,int value) {
        if(p.fixedDefense>=0)value=p.fixedDefense;
        for(var entry:continuous.entrySet()) {
            Piece source=pieces.get(entry.getKey());if(source==null)continue;
            for(var op:entry.getValue().operations())if(op.type().equals("MODIFY_DEF")
                &&condition(op.condition(),source,p.token,source.controller)&&select(op.target(),source,p.token,source.controller,op.filter()!=null?op.filter():entry.getValue().targets()==null?null:entry.getValue().targets().filter()).contains(p))value+=op.amount();
        }
        return Math.max(0,value);
    }''',1)

# Expiry restores both ATK and DEF modifiers.
s=s.replace('case "BOOST" -> p.boost-=expiry.value();\n                case "SET_POWER" -> p.fixedPower=expiry.value();',
'''case "BOOST" -> p.boost-=expiry.value();
                case "DEF_BOOST" -> p.defBoost-=expiry.value();
                case "SET_POWER","SET_ATK" -> p.fixedPower=expiry.value();
                case "SET_DEF" -> p.fixedDefense=expiry.value();''',1)

# Effect operation executor.
s=s.replace('case "MODIFY_POWER" -> {p.boost+=op.amount();if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"BOOST",op.amount(),expiryTurn(op.duration()),p.controller,null));else p.permanentBoost+=op.amount();cue(op.amount()>=0?"BUFF":"DEBUFF",source,p.token,l.actor,l.number);}',
'''case "MODIFY_POWER","MODIFY_ATK" -> {p.boost+=op.amount();if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"BOOST",op.amount(),expiryTurn(op.duration()),p.controller,null));else p.permanentBoost+=op.amount();cue(op.amount()>=0?"BUFF":"DEBUFF",source,p.token,l.actor,l.number);}
                            case "MODIFY_DEF" -> {p.defBoost+=op.amount();if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"DEF_BOOST",op.amount(),expiryTurn(op.duration()),p.controller,null));else p.permanentDefBoost+=op.amount();cue(op.amount()>=0?"SHIELD":"DEBUFF",source,p.token,l.actor,l.number);}''',1)
s=s.replace('case "SET_POWER" -> {int old=p.fixedPower;p.fixedPower=Math.max(0,op.amount());if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"SET_POWER",old,expiryTurn(op.duration()),p.controller,null));cue("BUFF",source,p.token,l.actor,l.number);}',
'''case "SET_POWER","SET_ATK" -> {int old=p.fixedPower;p.fixedPower=Math.max(0,op.amount());if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"SET_ATK",old,expiryTurn(op.duration()),p.controller,null));cue("BUFF",source,p.token,l.actor,l.number);}
                            case "SET_DEF" -> {int old=p.fixedDefense;p.fixedDefense=Math.max(0,op.amount());if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"SET_DEF",old,expiryTurn(op.duration()),p.controller,null));cue("SHIELD",source,p.token,l.actor,l.number);}
                            case "SWAP_ATK_DEF" -> {int atk=power(p),def=defense(p);int oldAtk=p.fixedPower,oldDef=p.fixedDefense;p.fixedPower=def;p.fixedDefense=atk;if(op.duration()!=null&&!op.duration().equals("PERMANENT")){expiries.add(new Expiry(p.token,"SET_ATK",oldAtk,expiryTurn(op.duration()),p.controller,null));expiries.add(new Expiry(p.token,"SET_DEF",oldDef,expiryTurn(op.duration()),p.controller,null));}cue("BUFF",source,p.token,l.actor,l.number);}''',1)

duel.write_text(s)

# ---------------------------------------------------------------------------
# 4) Client UI exposes ATK/DEF and an explicit MP1 -> End choice.
# ---------------------------------------------------------------------------
renderer=JAVA/'client/card/CardRenderer.java'
s=renderer.read_text()
s=s.replace('+"  ATK "+d.power()+"  •  "+tributeTag','+"  ATK "+d.power()+" / DEF "+d.defense()+"  •  "+tributeTag',1)
s=s.replace('+" • ATK "+d.power()','+" • ATK "+d.power()+" / DEF "+d.defense()',1)
renderer.write_text(s)

screen=JAVA/'client/screens/DuelScreen.java'
s=screen.read_text()
s=s.replace('(card.zone()==Duel.Zone.FIELD?"  "+card.power()+position:"")','(card.zone()==Duel.Zone.FIELD?"  ATK "+card.power()+" / DEF "+card.defense()+position:"")',1)
old='''        u.button(v.open() ? "Next Phase" : "Pass", new Rect(x, y + 225, 190, 36), false, priority,
            () -> a.send("duel", v.open() ? "next" : "pass", "", ""));
        u.button("cardworlds.ui.surrender", new Rect(x, y + 270, 190, 30), false, !a.state.spectator() && v.winner().isBlank(), () -> a.send("duel", "concede", "", ""));
        u.button("cardworlds.ui.reset_view", new Rect(x, y + 307, 190, 30), false, v.winner().isBlank(), scene::resetView);'''
new='''        String phaseAction=!v.open()?"Pass":v.phase().equals("MAIN1")&&v.turn()==1?"End Phase":v.phase().equals("MAIN1")?"Battle Phase":v.phase().equals("BATTLE")?"Main Phase 2":v.phase().equals("MAIN2")?"End Phase":v.phase().equals("END")?"End Turn":"Next Phase";
        u.button(phaseAction, new Rect(x, y + 225, 190, 36), false, priority,
            () -> a.send("duel", v.open() ? "next" : "pass", "", ""));
        u.button("End Turn", new Rect(x, y + 270, 190, 32), false,
            priority&&v.open()&&v.turnPlayer()==v.you()&&v.phase().equals("MAIN1")&&v.turn()>1,
            () -> a.send("duel","end","",""));
        u.button("cardworlds.ui.surrender", new Rect(x, y + 307, 190, 30), false, !a.state.spectator() && v.winner().isBlank(), () -> a.send("duel", "concede", "", ""));
        u.button("cardworlds.ui.reset_view", new Rect(x, y + 344, 190, 30), false, v.winner().isBlank(), scene::resetView);'''
if old not in s: raise SystemExit('DuelScreen phase controls anchor missing')
s=s.replace(old,new,1)
# Definition copy keeps real DEF instead of recomputing it.
s=s.replace('d.extra(),d.level(),d.power(),d.text()', 'd.extra(),d.level(),d.power(),d.defense(),d.text()',1)
screen.write_text(s)

# Localized effect terminology for the new independent stats.
for lang in ('en_us','vi_vn'):
    p=RES/f'assets/svarcade_tcg/lang/{lang}.json'; j=json.loads(p.read_text())
    if lang=='en_us':
        add={'cardworlds.operation.modify_atk':'Change %2$s ATK by %1$s','cardworlds.operation.modify_def':'Change %2$s DEF by %1$s',
             'cardworlds.operation.set_atk':'Set %2$s ATK to %1$s','cardworlds.operation.set_def':'Set %2$s DEF to %1$s',
             'cardworlds.operation.swap_atk_def':'Swap %2$s ATK and DEF','cardworlds.condition.atk_at_least':'%2$s has at least %1$s ATK',
             'cardworlds.condition.def_at_least':'%2$s has at least %1$s DEF'}
    else:
        add={'cardworlds.operation.modify_atk':'Thay đổi ATK của %2$s thêm %1$s','cardworlds.operation.modify_def':'Thay đổi DEF của %2$s thêm %1$s',
             'cardworlds.operation.set_atk':'Đặt ATK của %2$s thành %1$s','cardworlds.operation.set_def':'Đặt DEF của %2$s thành %1$s',
             'cardworlds.operation.swap_atk_def':'Hoán đổi ATK và DEF của %2$s','cardworlds.condition.atk_at_least':'%2$s có ít nhất %1$s ATK',
             'cardworlds.condition.def_at_least':'%2$s có ít nhất %1$s DEF'}
    j.update(add);p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')

# ---------------------------------------------------------------------------
# 5) Signature VFX registry from the strict build, without card-ID gameplay branches.
# ---------------------------------------------------------------------------
effect_content=JAVA/'data/EffectContent.java'
s=effect_content.read_text()
if 'SIGNATURES=loadSignatures()' not in s:
    s=s.replace('private static final Map<String,EffectSpec.Presentation> ACTIONS=loadActions();',
'''private static final Map<String,EffectSpec.Presentation> ACTIONS=loadActions();
    private static final Map<String,EffectSpec.Presentation> SIGNATURES=loadSignatures();
    private static Map<String,EffectSpec.Presentation> loadSignatures() {
        try(var in=EffectContent.class.getResourceAsStream("/data/svarcade_tcg/signature_presentations.json")){
            if(in==null)return Map.of();
            Map<String,EffectSpec.Presentation> loaded=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,EffectSpec.Presentation>>(){}.getType());
            return loaded==null?Map.of():Map.copyOf(loaded);
        }catch(Exception e){return Map.of();}
    }
    private static EffectSpec.Presentation signature(Catalog.Card card){
        EffectSpec.Presentation exact=SIGNATURES.get(card.id());if(exact!=null)return exact;
        try{return SIGNATURES.get(CardGameplayEffects.key(card));}catch(RuntimeException ignored){return null;}
    }''',1)
    old='''    public static EffectSpec.Presentation presentation(Catalog.Card card){
        return card.effect()!=null&&card.effect().spec()!=null&&card.effect().spec().vfx()!=null?card.effect().spec().vfx():ACTIONS.get(actionKey(card.type()));
    }'''
    new='''    public static EffectSpec.Presentation presentation(Catalog.Card card){
        var authored=signature(card);if(authored!=null)return authored;
        return card.effect()!=null&&card.effect().spec()!=null&&card.effect().spec().vfx()!=null?card.effect().spec().vfx():ACTIONS.get(actionKey(card.type()));
    }'''
    if old not in s: raise SystemExit('EffectContent presentation anchor missing')
    s=s.replace(old,new,1)
effect_content.write_text(s)

# Author + validate strict distinctness only after all other resource generators have run.
runpy.run_path(str(ROOT/'source-overrides/ygo-fidelity/strict_effects.py'),run_name='__main__')

# ---------------------------------------------------------------------------
# 6) Runtime uniqueness guard: exact mechanics, unique effect wording/name,
#    and minimum 10/12 functional signature distance for authored Pokemon.
# ---------------------------------------------------------------------------
ident=JAVA/'data/CardIdentities.java'
s=ident.read_text()
start=s.index('    public static void validateUniqueness(Catalog catalog){')
end=s.index('    private CardIdentities(){}',start)
new_method='''    private static List<String> strictSignature(Catalog.Card card){
        var spec=card.effect()==null?null:card.effect().spec();if(spec==null)return List.of();
        List<String> out=new ArrayList<>();
        for(var stage:EffectSpec.list(spec.stages()))if(stage.id().startsWith("identity_facet_v5"))
            for(var op:EffectSpec.list(stage.effect().spec().operations()))out.add(op.type()+"|"+EffectSpec.value(op.target(),""));
        return out;
    }
    public static void validateUniqueness(Catalog catalog){
        Map<String,String> mechanics=new HashMap<>(),primary=new HashMap<>(),wording=new HashMap<>();int external=0;
        List<Catalog.Card> authored=new ArrayList<>();
        for(var c:catalog.cards().values()){
            if(c.effect()==null||c.effect().spec()==null)throw new IllegalArgumentException(c.id()+": effect is missing");
            String duplicate=mechanics.putIfAbsent(mechanics(c.effect(),false),c.id());
            if(duplicate!=null)throw new IllegalArgumentException(c.id()+": duplicate executable effect graph of "+duplicate);
            duplicate=primary.putIfAbsent(mechanics(c.effect(),true),c.id());
            if(duplicate!=null)throw new IllegalArgumentException(c.id()+": duplicate primary gameplay effect of "+duplicate);
            var def=CardGameplayEffects.definition(c);
            if(def!=null){
                String words=def.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
                duplicate=wording.putIfAbsent(words,c.id());if(duplicate!=null)throw new IllegalArgumentException(c.id()+": duplicate effect wording/title of "+duplicate);
                if(strictSignature(c).size()!=12)throw new IllegalArgumentException(c.id()+": strict gameplay signature must contain 12 functional slots");
                authored.add(c);
            }else if(c.category().equals("pokemon")&&!c.id().startsWith("special_"))external++;
        }
        for(int i=0;i<authored.size();i++)for(int j=i+1;j<authored.size();j++){
            var a=strictSignature(authored.get(i)),b=strictSignature(authored.get(j));int distance=0;
            for(int k=0;k<12;k++)if(!a.get(k).equals(b.get(k)))distance++;
            if(distance<10)throw new IllegalArgumentException(authored.get(i).id()+" / "+authored.get(j).id()+": near-duplicate gameplay signature distance "+distance+"/12");
        }
        org.slf4j.LoggerFactory.getLogger("cardworlds-gameplay").info("CARDWORLDS_GAMEPLAY_IDENTITIES catalog={} authored={} external={} minSignatureDistance=10/12 duplicatePrimary=0 duplicateMechanics=0 duplicateWording=0",catalog.cards().size(),authored.size(),external);
    }
'''
s=s[:start]+new_method+s[end:]
ident.write_text(s)

# ---------------------------------------------------------------------------
# 7) Regression tests: DEF battle maths and first-turn/MP1 phase rules.
# ---------------------------------------------------------------------------
test=ROOT/'src/test/java/vn/svarcade/tcg/EngineTest.java'
s=test.read_text()
insert='''    @Test void monsterCardsExposeIndependentDefense(){assertTrue(base.card("charmander").defense()>0);assertNotEquals(base.card("charmander").power(),base.card("charmander").defense());}
    @Test void firstTurnSkipsBattleAndMain2(){Duel d=duel();main(d);assertEquals("MAIN1",d.view(0).phase());act(d,0,"next","","");act(d,1,"pass","","");assertEquals("END",d.view(0).phase());}
    @Test void mainPhaseOneCanEndWithoutConductingBattle(){Duel d=duel();main(d);act(d,0,"end","","");act(d,1,"pass","","");assertEquals("END",d.view(0).phase());}
'''
idx=s.rfind('\n}')
if idx<0: raise SystemExit('EngineTest closing brace missing')
s=s[:idx]+'\n'+insert+s[idx:]
test.write_text(s)

print('CARDWORLDS_YGO_FIDELITY_APPLIED atkDef=true firstTurnBattleSkip=true main1End=true damageStepGate=true strictIdentityV5=true')
