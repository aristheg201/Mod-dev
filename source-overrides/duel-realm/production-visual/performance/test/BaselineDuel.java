package vn.svarcade.tcg.duel;

import vn.svarcade.tcg.data.Catalog;
import java.util.*;

/** No Minecraft, collection or rendering dependencies. All mutations enter through validated actions. */
public final class BaselineDuel {
    public enum Phase { DRAW, STANDBY, MAIN1, BATTLE, MAIN2, END }
    public enum Zone { DECK, HAND, FIELD, SUPPORT, STADIUM, DISCARD, BANISHED, EXTRA }
    public enum BattlePosition { ATTACK, DEFENSE, FACE_DOWN_DEFENSE }
    public enum Cause { DRAW, PLAY, SET, TRIBUTE, TRIBUTE_SUMMON, SPECIAL_SUMMON, SUMMON_STAGE, EVOLVE, EXTRA_SUMMON, DISCARD, DESTROY, BATTLE, BANISH, RETURN, REVIVE, EFFECT, SYSTEM }
    public record Event(long sequence, String kind, String card, int owner, int controller, Zone from, Zone to, Cause cause, String source, int chainLink) {}
    public record Action(String kind, String card, String target) {}
    public record VisibleCard(String token, String name, String species, List<String> aspects, String type, String category, int power, String text, Zone zone, int controller, String position,Catalog.Effect effect,Map<String,Integer> counters) {public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position,Catalog.Effect effect){this(token,name,species,aspects,type,category,power,text,zone,controller,position,effect,Map.of());}public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position){this(token,name,species,aspects,type,category,power,text,zone,controller,position,null,Map.of());}}
    public record View(long revision, int you, int turnPlayer, int priority, int turn, String phase, boolean open,
                       List<Integer> life, List<Integer> deckCounts, List<Integer> handCounts, List<Integer> extraCounts, List<VisibleCard> cards,
                       List<String> chain, String winner, List<String> log, List<Cue> cues) {}
    static final class Piece {
        final String token; final Catalog.Card card; final int owner; int controller; Zone zone; int boost, shield, generation; boolean attacked;
        int fixedPower=-1,extraAttacks,lookedBy=-1,permanentBoost;
        Catalog.Effect grantedEffect;
        final Set<Integer> inspectedBy=new HashSet<>();final Set<String> flags=new HashSet<>();final Map<String,Integer> counters=new HashMap<>();
        BattlePosition position=BattlePosition.ATTACK; int summonedTurn=-1, positionTurn=-1;
        Piece(String token,Catalog.Card card,int owner,Zone zone) { this.token=token;this.card=card;this.owner=owner;this.controller=owner;this.zone=zone; }
    }
    static final class Link {
        final Piece source; String target; final int actor; final Catalog.Effect effect; final int number;
        final EffectContext context; String stageId="primary"; Event triggerEvent; boolean negated, activationNegated; int targetGeneration=-1;
        Link(Piece p,String target,int actor,int number) {this(p,target,actor,number,p.card.effect());}
        Link(Piece p,String target,int actor,int number,Catalog.Effect effect) {source=p;this.target=target;this.actor=actor;this.effect=effect;this.context=new EffectContext(p.token,actor,number);this.number=number;}
    }
    private final Catalog catalog;
    private final LinkedHashMap<String,Piece> pieces=new LinkedHashMap<>();
    private final List<Link> chain=new ArrayList<>();
    private final List<Event> history=new ArrayList<>();
    private final List<Event> pendingEvents=new ArrayList<>();
    private final List<String> log=new ArrayList<>();
    private final Set<String> used=new HashSet<>();
    private final Set<String> faceDown=new HashSet<>();
    private final Map<String,Integer> setTurn=new HashMap<>();
    private final Map<Integer,SummonFramework.Progress> summonProgress=new HashMap<>();
    private final int[] life,normal={0,0};
    private int turnPlayer=0,priority=0,turn=1,passes=0,winner=-1;
    private long revision;
    private Phase phase=Phase.DRAW;
    private boolean open=true,advance=false;
    private String attacker,target;
    public BaselineDuel(Catalog catalog,List<String> a,List<String> ae,List<String> b,List<String> be,Random random) {
        this.catalog=catalog; life=new int[]{catalog.rules().life(),catalog.rules().life()};
        addDeck(0,a,ae,random);addDeck(1,b,be,random);
        for(int i=0;i<catalog.rules().hand();i++){draw(0);draw(1);}pendingEvents.clear();note("The duel begins.");
    }
    private void addDeck(int player,List<String> deck,List<String> extra,Random rng) {
        var errors=catalog.deckErrors(deck,extra,false); if(!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ",errors));
        List<String> shuffled=new ArrayList<>(deck);Collections.shuffle(shuffled,rng);
        for(String id:shuffled) addPiece(player,id,Zone.DECK);
        for(String id:extra) addPiece(player,id,Zone.EXTRA);
    }
    private void addPiece(int player,String id,Zone zone) {String token=UUID.randomUUID().toString();pieces.put(token,new Piece(token,catalog.card(id),player,zone));}
    public synchronized void act(int actor,Action action,long expectedRevision) {
        require(actor==0||actor==1,"You are not a duelist.");
        require(winner<0,"This duel has ended.");require(expectedRevision==revision,"The board changed. Try again.");
        if(action.kind.equals("concede")) {winner=1-actor;note("A duelist conceded.");revision++;return;}
        require(actor==priority,"Wait for your response window.");
        switch(action.kind) {
            case "pass" -> pass();
            case "next" -> {require(open&&actor==turnPlayer,"Finish the response window first.");advance=true;open=false;passes=1;priority=1-actor;}
            case "play" -> play(actor,action.card,action.target);
            case "set_monster" -> setMonster(actor,action.card,action.target);
            case "position" -> changePosition(actor,action.card,action.target);
            case "activate" -> {conditionEvent=optionalTriggers.get(action.card);try{activate(actor,action.card,action.target);optionalTriggers.remove(action.card);}finally{conditionEvent=null;}}
            case "activate_stage" -> activateStage(actor,action.card,action.target);
            case "attack" -> attack(actor,action.card,action.target);
            default -> throw new IllegalArgumentException("Unknown duel action.");
        }
        if(winner<0){if(chain.isEmpty())collectTriggers();else collectChainReactions();}
        revision++;
    }
    private void mainAction(int actor) {require(open&&actor==turnPlayer&&(phase==Phase.MAIN1||phase==Phase.MAIN2),"Play cards during your Main Phase.");}
    private Piece owned(int actor,String token) {Piece p=pieces.get(token);require(p!=null&&p.controller==actor,"Choose one of your cards.");return p;}
    private void play(int actor,String token,String material) {
        mainAction(actor);Piece p=owned(actor,token);require(summonLocks[actor]<turn&&!hasFlag(p,"CANNOT_SUMMON"),"cardworlds.error.cannot_summon");require(p.zone==Zone.HAND||p.zone==Zone.EXTRA,"That card cannot be played here.");
        if(!p.card.category().equals("pokemon")) {
            require(p.zone==Zone.HAND,"Only a card in your hand can be Set.");
            require("SET".equalsIgnoreCase(material),"Use Activate, or Set this Spell/Trap first.");
            setSupport(actor,p);return;
        }
        String evolution=p.card.evolvesFrom()==null?"":p.card.evolvesFrom();
        List<String> materials=materials(material);
        SummonFramework.Profile advanced=SummonFramework.forFirstStage(p.card.id());
        if(p.card.extra()&&advanced!=null){advancedSummon(actor,p,advanced,materials);return;}

        if(p.card.extra()) {
            require(!evolution.isBlank(),"This Extra Deck form has no evolution material.");
            require(materials.size()==1,"Choose the required evolution material.");
            Piece previous=owned(actor,materials.getFirst());
            require(previous.zone==Zone.FIELD&&previous.card.id().equals(evolution),"Choose the required evolution.");
            p.boost=previous.boost;p.shield=previous.shield;p.attacked=previous.attacked;
            move(previous,Zone.DISCARD,Cause.EVOLVE,p.token,0);
            move(p,Zone.FIELD,Cause.EXTRA_SUMMON,token,0);p.position=BattlePosition.ATTACK;p.summonedTurn=turn;p.positionTurn=turn;
            note(p.card.name()+" evolves from the Extra Deck.");window();return;
        }

        if(!evolution.isBlank()&&materials.size()==1) {
            Piece candidate=pieces.get(materials.getFirst());
            if(candidate!=null&&candidate.controller==actor&&candidate.zone==Zone.FIELD&&candidate.card.id().equals(evolution)) {
                p.boost=candidate.boost;p.shield=candidate.shield;p.attacked=candidate.attacked;
                move(candidate,Zone.DISCARD,Cause.EVOLVE,p.token,0);
                move(p,Zone.FIELD,Cause.EVOLVE,token,0);p.position=BattlePosition.ATTACK;p.summonedTurn=turn;p.positionTurn=turn;
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
        move(p,Zone.FIELD,cause,token,0);p.position=BattlePosition.ATTACK;p.summonedTurn=turn;p.positionTurn=turn;
        note(required>0 ? p.card.name()+" is Tribute Summoned." : p.card.name()+" is Normal Summoned.");window();
    }
    private static List<String> materials(String raw) {
        if(raw==null||raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s->!s.isBlank()).toList();
    }

    private void setMonster(int actor,String token,String material) {
        mainAction(actor);Piece p=owned(actor,token);require(summonLocks[actor]<turn&&!hasFlag(p,"CANNOT_SUMMON"),"cardworlds.error.cannot_summon");require(p.zone==Zone.HAND&&p.card.category().equals("pokemon"),"Choose a Pokemon in your hand.");
        require(normal[actor]<catalog.rules().normalSummons(),"You have used your Normal Summon this turn.");
        int required=p.card.tributeCount();List<String> materials=materials(material);
        require(materials.size()==required, required==0 ? "This Pokemon does not require a Tribute." : "Level "+p.card.level()+" requires "+required+(required==1?" Tribute.":" Tributes."));
        LinkedHashSet<String> unique=new LinkedHashSet<>(materials);require(unique.size()==materials.size(),"Choose different Tribute Pokemon.");
        List<Piece> tributes=new ArrayList<>();for(String id:materials){Piece tribute=owned(actor,id);require(tribute.zone==Zone.FIELD,"Tributes must be your Pokemon on the field.");tributes.add(tribute);}
        require(count(actor,Zone.FIELD)-tributes.size()<catalog.rules().pokemonZones(),"Your field is full.");
        for(Piece tribute:tributes)move(tribute,Zone.DISCARD,Cause.TRIBUTE,p.token,0);normal[actor]++;
        move(p,Zone.FIELD,required>0?Cause.TRIBUTE_SUMMON:Cause.SET,p.token,0);p.position=BattlePosition.FACE_DOWN_DEFENSE;p.summonedTurn=turn;p.positionTurn=turn;
        note(required>0?"A Pokemon is Tribute Set face-down.":"A Pokemon is Set face-down in Defense Position.");window();
    }
    private void changePosition(int actor,String token,String requested) {
        mainAction(actor);Piece p=owned(actor,token);require(p.zone==Zone.FIELD&&p.card.category().equals("pokemon"),"Choose one of your Pokemon on the field.");
        require(!summonLocked(p),"This summon stage is locked.");require(!p.attacked,"A Pokemon that attacked cannot change position this turn.");
        require(p.summonedTurn!=turn,"A Pokemon cannot manually change position on the turn it was Summoned or Set.");require(p.positionTurn!=turn,"This Pokemon already changed position this turn.");
        BattlePosition next;
        if(p.position==BattlePosition.FACE_DOWN_DEFENSE)next=BattlePosition.ATTACK;
        else if(requested==null||requested.isBlank()||requested.equalsIgnoreCase("TOGGLE"))next=p.position==BattlePosition.ATTACK?BattlePosition.DEFENSE:BattlePosition.ATTACK;
        else next=BattlePosition.valueOf(requested.toUpperCase(Locale.ROOT));
        require(next!=BattlePosition.FACE_DOWN_DEFENSE,"Use Set Monster to place a Pokemon face-down.");
        BattlePosition previous=p.position;p.position=next;p.positionTurn=turn;cue("POSITION_CHANGE",p,"",actor,0);signal("ON_POSITION_CHANGE",p,null);if(previous==BattlePosition.FACE_DOWN_DEFENSE)signal("ON_FLIP",p,null);note(p.card.name()+" changes to "+(next==BattlePosition.ATTACK?"Attack":"Defense")+" Position.");window();
    }

    private void setSupport(int actor,Piece p) {
        SpellTrapRules.Profile profile=SpellTrapRules.profile(p.card);require(profile!=null,"This card cannot be Set.");
        Zone destination=profile.kind()==SpellTrapRules.Kind.FIELD_SPELL?Zone.STADIUM:Zone.SUPPORT;
        if(destination==Zone.SUPPORT)require(count(actor,Zone.SUPPORT)<catalog.rules().supportZones(),"Your Spell/Trap row is full.");
        if(destination==Zone.STADIUM)for(Piece old:new ArrayList<>(pieces.values()))if(old.controller==actor&&old.zone==Zone.STADIUM)move(old,Zone.DISCARD,Cause.EFFECT,p.token,0);
        move(p,destination,Cause.SET,p.token,0);signal("ON_SET",p,null);faceDown.add(p.token);setTurn.put(p.token,turn);note("A card was Set.");window();
    }
    private void advancedSummon(int actor,Piece result,SummonFramework.Profile profile,List<String> materialTokens) {
        require(result.zone==Zone.EXTRA,"Advanced summon result must come from the Extra Deck.");
        require(!profile.stages().isEmpty()&&profile.stages().getFirst().card().equals(result.card.id()),"Invalid advanced summon stage.");
        require(materialTokens.size()==profile.materialCount(),"Wrong number of advanced summon materials.");
        LinkedHashSet<String> unique=new LinkedHashSet<>(materialTokens);require(unique.size()==materialTokens.size(),"Choose different summon materials.");
        List<Piece> chosen=new ArrayList<>();for(String id:materialTokens){Piece piece=owned(actor,id);require(piece.zone==Zone.FIELD,"Advanced summon materials must be on your Monster Zones.");chosen.add(piece);}
        for(SummonFramework.Material rule:profile.materials()){
            long have=chosen.stream().filter(x->x.card.id().equals(rule.card())&&x.zone.name().equals(rule.zone())).count();
            require(have>=rule.count(),"Missing material: "+rule.card()+" x"+rule.count());
        }
        require(count(actor,Zone.FIELD)-chosen.size()<catalog.rules().pokemonZones(),"Your Monster Zones are full.");
        for(Piece piece:chosen)move(piece,Zone.DISCARD,Cause.SPECIAL_SUMMON,result.token,0);
        move(result,Zone.FIELD,Cause.SPECIAL_SUMMON,result.token,0);result.position=BattlePosition.ATTACK;result.summonedTurn=turn;result.positionTurn=turn;
        SummonFramework.Stage stage=profile.stages().getFirst();summonProgress.put(actor,new SummonFramework.Progress(profile.id(),0,stage.lockTurns(),result.token));
        note(stage.label()+" is Special Summoned.");window();
    }
    private boolean summonLocked(Piece p){SummonFramework.Progress progress=summonProgress.get(p.controller);return progress!=null&&progress.token().equals(p.token)&&progress.turnsRemaining()>0;}
    private void tickAdvancedSummons(int actor){
        SummonFramework.Progress progress=summonProgress.get(actor);if(progress==null)return;
        Piece current=pieces.get(progress.token());if(current==null||current.zone!=Zone.FIELD){summonProgress.remove(actor);return;}
        if(progress.turnsRemaining()>0){progress.turnsRemaining(progress.turnsRemaining()-1);if(progress.turnsRemaining()>0){note(current.card.name()+" remains locked for "+progress.turnsRemaining()+" turn(s).");return;}}
        SummonFramework.Profile profile=SummonFramework.byId(progress.profileId());if(profile==null){summonProgress.remove(actor);return;}
        int nextIndex=progress.stageIndex()+1;if(nextIndex>=profile.stages().size()){summonProgress.remove(actor);return;}
        SummonFramework.Stage next=profile.stages().get(nextIndex);
        Piece replacement=pieces.values().stream().filter(x->x.owner==actor&&x.zone==Zone.EXTRA&&x.card.id().equals(next.card())).findFirst().orElse(null);
        if(replacement==null){note("Summon sequence paused: "+next.card()+" is not in the Extra Deck.");return;}
        replacement.boost=current.boost;replacement.shield=current.shield;replacement.attacked=current.attacked;
        move(current,Zone.BANISHED,Cause.SUMMON_STAGE,replacement.token,0);move(replacement,Zone.FIELD,Cause.SUMMON_STAGE,current.token,0);replacement.position=BattlePosition.ATTACK;replacement.summonedTurn=turn;replacement.positionTurn=turn;
        progress.stageIndex(nextIndex);progress.turnsRemaining(next.lockTurns());progress.token(replacement.token);
        if(nextIndex==profile.stages().size()-1)summonProgress.remove(actor);else summonProgress.put(actor,progress);
        note(next.label()+" enters the next summon stage.");
    }

    private void activate(int actor,String token,String chosen) {
        Piece p=owned(actor,token);Catalog.Effect e=activationOverride==null?effectiveEffect(p):activationOverride;require(e!=null,"This card has no activated effect.");
        require(!summonLocked(p),"This summon stage is locked.");require(e.spec()==null||e.spec().triggers().contains("ON_ACTIVATE")||e.spec().triggers().contains("CONTINUOUS")||e.spec().optional()&&optionalTriggers.containsKey(p.token),"cardworlds.error.automatic_trigger");
        require(p.zone==Zone.HAND||p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM,"This effect cannot be used from here.");
        require(e.phases().contains(phase.name()),"This effect cannot be used in this phase.");
        if(e.speed()==1) mainAction(actor);
        if(!chain.isEmpty()) require(e.speed()>=2&&e.speed()>=chain.getLast().effect.speed(),"This effect cannot respond to that chain.");
        if(!open) require(e.speed()>=2,"Choose a fast response.");
        require(!e.oncePerTurn()||!used.contains(activationOverride==null?actor+":"+p.card.id():turn+":"+limitKey(p,e,validatingStage)),"You already used this effect this turn.");
        require(life[actor]>e.lifeCost(),"Not enough Trainer Life to pay the cost.");
        validateTarget(actor,e,chosen);
        List<Payment> paymentPlan=validateSpec(p,e,chosen,actor);
        boolean fromHand=p.zone==Zone.HAND;
        SpellTrapRules.Profile spellTrap=SpellTrapRules.profile(p.card);
        boolean wasSet=faceDown.contains(p.token);
        if(spellTrap!=null){
            if(fromHand)require(spellTrap.activateFromHand(),"This Trap must be Set before activation.");
            if(wasSet)require(turn-setTurn.getOrDefault(p.token,turn)>=spellTrap.minSetTurns(),"A Set Trap/Quick-Play card cannot be activated this turn.");
        }
        if(fromHand) require(!p.card.category().equals("pokemon"),"Play this Pokemon before using its ability.");
        if(fromHand&&!p.card.category().equals("stadium")) require(count(actor,Zone.SUPPORT)<catalog.rules().supportZones(),"Your support row is full.");
        // Everything above is validation. Costs below are committed even if resolution later fails.
        life[actor]-=e.lifeCost();if(e.oncePerTurn())used.add(activationOverride==null?actor+":"+p.card.id():turn+":"+limitKey(p,e,validatingStage));
        paySpec(p,e,paymentPlan,actor);
        if(wasSet)faceDown.remove(p.token);
        if(fromHand&&p.zone==Zone.HAND) {
            if(p.card.category().equals("stadium")) {for(Piece old:new ArrayList<>(pieces.values())) if(old.controller==actor&&old.zone==Zone.STADIUM)move(old,Zone.DISCARD,Cause.EFFECT,p.token,0); move(p,Zone.STADIUM,Cause.PLAY,p.token,0);}
            else move(p,Zone.SUPPORT,Cause.PLAY,p.token,0);
        }
        Link link=new Link(p,chosen,actor,chain.size()+1,e);Piece targetPiece=pieces.get(chosen);if(targetPiece!=null)link.targetGeneration=targetPiece.generation;
        link.triggerEvent=conditionEvent;captureEvent(link);for(var payment:paymentPlan)for(Piece paid:payment.cards())link.context.paidCosts.add(new EffectContext.Selection(paid.token,paid.generation));chain.add(link);note("Chain "+chain.size()+": "+p.card.name());
        cue(p.card.category().equals("pokemon")?"CAST_STATUS":p.card.category().equals("reaction")?"TRAP_REVEAL":"SPELL_ACTIVATE",p,chosen,actor,link.number);
        cue("CHAIN_LINK",p,chosen,actor,link.number);signal("ON_CHAIN",p,targetPiece);signal("ON_CHAINED",p,targetPiece);
        advance=false;open=false;passes=0;priority=1-actor;
    }
    private void validateTarget(int actor,Catalog.Effect e,String selected) {
        if(e.spec()!=null&&e.spec().targets()!=null){
            var spec=e.spec();var candidates=select(spec.targets().selector(),null,selected,actor,spec.targets().filter());
            if(!spec.targets().selector().equals("SELF")&&!spec.targets().selector().equals("SOURCE"))
                require(candidates.size()>=spec.targets().min(),"cardworlds.error.target");
        }
        if(e.target().equals("none")) return;
        if(e.target().equals("chain")) {require(!chain.isEmpty()&&Integer.toString(chain.size()).equals(selected),"Choose the latest chain link.");return;}
        Piece p=pieces.get(selected);require(p!=null,"Choose a target.");require(p.controller==actor||!hasFlag(p,"PREVENT_TARGET"),"cardworlds.error.protected_target");
        switch(e.target()) {
            case "enemy" -> require(p.zone==Zone.FIELD&&p.controller!=actor,"Choose an opposing Pokemon.");
            case "ally" -> require(p.zone==Zone.FIELD&&p.controller==actor,"Choose one of your Pokemon.");
            case "grave" -> require(p.zone==Zone.DISCARD&&p.owner==actor&&p.card.category().equals("pokemon")&&!p.card.extra(),"Choose a Pokemon in your discard pile.");
            default -> throw new IllegalArgumentException("Unsupported target rule.");
        }
    }
    private void attack(int actor,String token,String selected) {
        require(open&&actor==turnPlayer&&phase==Phase.BATTLE&&turn>1,"You cannot attack now.");
        Piece p=owned(actor,token);require(p.zone==Zone.FIELD&&(!p.attacked||p.extraAttacks>0),"That Pokemon cannot attack again.");require(!hasFlag(p,"CANNOT_ATTACK"),"cardworlds.error.cannot_attack");require(p.position==BattlePosition.ATTACK,"Only an Attack Position Pokemon can attack.");require(!summonLocked(p),"This summon stage is locked.");
        if(selected==null||selected.isBlank()) require(count(1-actor,Zone.FIELD)==0,"An opposing Pokemon blocks a direct attack.");
        else {Piece t=pieces.get(selected);require(t!=null&&t.zone==Zone.FIELD&&t.controller!=actor,"Choose an opposing Pokemon.");}
        if(p.attacked)p.extraAttacks--;p.attacked=true;attacker=token;target=selected;
        var meta=vn.svarcade.tcg.data.EffectContent.attackPresentation(p.card);
        String mode=meta==null?"MELEE":vn.svarcade.tcg.data.EffectSpec.value(meta.mode(),"MELEE");
        cue("CHARGE",p,selected,actor,0);
        if(selected==null||selected.isBlank())cue("DIRECT_ATTACK",p,"",actor,0);
        signal("ON_ATTACK_DECLARE",p,pieces.get(selected));if(pieces.get(selected)!=null)signal("ON_ATTACKED",pieces.get(selected),p);note(p.card.name()+" declares an attack.");window();
    }
    private void window() {open=false;passes=0;priority=turnPlayer;}
    private void pass() {
        if(open){advance=true;open=false;passes=1;priority=1-turnPlayer;return;}
        if(++passes<2){priority=1-priority;return;}
        passes=0;
        if(!chain.isEmpty()) {resolve();if(winner<0)window();return;}
        if(attacker!=null){battle();attacker=null;target=null;if(winner<0)window();return;}
        if(advance){advance=false;nextPhase();}
        open=true;priority=turnPlayer;
    }
    private void resolve() {
        for(int i=chain.size()-1;i>=0;i--) {
            Link l=chain.get(i);
            cue("CHAIN_RESOLVE",l.source,l.target,l.actor,l.number);signal("ON_CHAIN_RESOLVE",l.source,pieces.get(l.target));
            if(!l.negated&&!l.activationNegated) effect(l);
            else {note(l.source.card.name()+" was negated.");cue("CHAIN_NEGATE",l.source,l.target,l.actor,l.number);signal("ON_NEGATE",l.source,null);}
            if((l.source.zone==Zone.SUPPORT&&!SpellTrapRules.persists(l.source.card))||(l.activationNegated&&l.source.zone==Zone.STADIUM))move(l.source,Zone.DISCARD,Cause.EFFECT,l.source.token,l.number);
            if(winner>=0)break;
        }
        chain.clear();
    }
    private void effect(Link l) {
        Piece t=pieces.get(l.target);Catalog.Effect e=l.effect;
        if(!e.target().equals("none")&&!e.target().equals("chain")) {
            if(t==null||t.generation!=l.targetGeneration){note(l.source.card.name()+" lost its target.");if(e.spec()==null||!e.operation().equals("composite"))return;l.target="";t=null;}
            try{validateTarget(l.actor,e,l.target);}catch(IllegalArgumentException ex){note(l.source.card.name()+" lost its target.");if(e.spec()==null||!e.operation().equals("composite"))return;l.target="";t=null;}
        }
        if(e.spec()!=null&&e.operation().equals("composite")){
            conditionEvent=l.triggerEvent;resolvingContext=l.context;
            try {for(var c:vn.svarcade.tcg.data.EffectSpec.list(e.spec().resolutionConditions()))if(!condition(c,l.source,l.target,l.actor))return;
            if(!e.spec().triggers().contains("CONTINUOUS"))runOperations(l,e.spec().operations(),0);
            else {activatedContinuous.add(l.source.token);syncContinuous();}
            note(l.source.card.name()+" resolves.");return;}finally{conditionEvent=null;resolvingContext=null;}
        }
        switch(e.operation()) {
            case "damage" -> damage(1-l.actor,e.amount());
            case "heal" -> {life[l.actor]=Math.addExact(life[l.actor],e.amount());cue("HEAL",l.source,l.target,l.actor,l.number);}
            case "draw" -> {for(int i=0;i<e.amount()&&winner<0;i++)draw(l.actor);}
            case "destroy" -> {if(protectFromDestruction(t,l.source,Cause.DESTROY)||hasFlag(t,"PREVENT_DESTROY"))cue("SHIELD",t,l.source.token,t.controller,l.number);else if(t.shield>0)t.shield--;else move(t,Zone.DISCARD,Cause.DESTROY,l.source.token,l.number);}
            case "banish" -> move(t,Zone.BANISHED,Cause.BANISH,l.source.token,l.number);
            case "return" -> move(t,t.card.extra()?Zone.EXTRA:Zone.HAND,Cause.RETURN,l.source.token,l.number);
            case "revive" -> {if(count(l.actor,Zone.FIELD)<catalog.rules().pokemonZones()){move(t,Zone.FIELD,Cause.REVIVE,l.source.token,l.number);t.position=BattlePosition.ATTACK;t.summonedTurn=turn;t.positionTurn=turn;}}
            case "shield" -> {t.shield+=e.amount();cue("SHIELD",l.source,t.token,l.actor,l.number);}
            case "boost" -> {t.boost+=e.amount();cue("BUFF",l.source,t.token,l.actor,l.number);}
            case "negate_effect", "negate_activation" -> {int n=Integer.parseInt(l.target)-1;if(n>=0&&n<l.number-1){if(e.operation().equals("negate_effect"))chain.get(n).negated=true;else chain.get(n).activationNegated=true;cue("CHAIN_NEGATE",l.source,chain.get(n).source.token,l.actor,n+1);signal("ON_NEGATE",l.source,chain.get(n).source);}}
            default -> throw new IllegalStateException("Unvalidated effect");
        }
        note(l.source.card.name()+" resolves.");
    }
    private void battle() {
        Piece a=pieces.get(attacker),d=pieces.get(target);
        if(a==null||a.zone!=Zone.FIELD||a.controller!=turnPlayer)return;
        if(target==null||target.isBlank()) {if(count(1-turnPlayer,Zone.FIELD)==0){cue("ATTACK_SPECIAL",a,"",a.controller,0);damage(1-turnPlayer,power(a));}else {a.attacked=false;note("The field changed. Choose a new attack.");}return;}
        if(d==null||d.zone!=Zone.FIELD||d.controller==turnPlayer){a.attacked=false;note("The target left the field. Choose a new attack.");return;}
        var action=vn.svarcade.tcg.data.EffectContent.attackPresentation(a.card);
        String mode=action==null?"MELEE":vn.svarcade.tcg.data.EffectSpec.value(action.mode(),"MELEE");
        cue(mode.equals("MELEE")?"ATTACK_PHYSICAL":mode.equals("STATUS")?"CAST_STATUS":"ATTACK_SPECIAL",a,d.token,a.controller,0);
        int attack=power(a)+(catalog.rules().effective().getOrDefault(a.card.type(),List.of()).contains(d.card.type())?catalog.rules().typeBonus():0);
        if(d.position==BattlePosition.FACE_DOWN_DEFENSE){d.position=BattlePosition.DEFENSE;signal("ON_FLIP",d,a);cue("POSITION_CHANGE",d,a.token,d.controller,0);note(d.card.name()+" is flipped face-up in Defense Position.");}
        cue("IMPACT",a,d.token,a.controller,0);
        int difference=attack-power(d);
        if(d.position==BattlePosition.DEFENSE){
            if(difference>0){destroyBattle(d,a);if(hasFlag(a,"PIERCE"))damage(d.controller,difference);}else if(difference<0)damage(a.controller,-difference);
            return;
        }
        if(difference>=0)destroyBattle(d,a);
        if(difference<=0)destroyBattle(a,d);
        if(difference>0)damage(d.controller,difference);else if(difference<0)damage(a.controller,-difference);
    }
    private int power(Piece p) {if(p.zone!=Zone.FIELD)return p.card.power();int result=p.card.power()+p.boost;for(Piece source:pieces.values())if(source.controller==p.controller&&source.card.modifiers()!=null)for(var m:source.card.modifiers())if(source.zone.name().equals(m.zone())&&(m.affectedType().equals("any")||m.affectedType().equals(p.card.type())))result+=m.power();return compositePower(p,result);}
    /** Initial mandatory trigger subset; events created during resolution wait for the whole chain. */
    private void collectTriggers(){
        List<Event> events=List.copyOf(pendingEvents);pendingEvents.clear();
        collectCompositeTriggers(events);
        for(int seat:new int[]{turnPlayer,1-turnPlayer})for(Piece source:pieces.values()){
            if(source.controller!=seat||source.card.triggers()==null)continue;
            for(int index=0;index<source.card.triggers().size();index++){
                var t=source.card.triggers().get(index);String scope=seat+":"+source.card.id()+":trigger:"+index;
                if(!source.zone.name().equals(t.zone())||t.effect().oncePerTurn()&&used.contains(scope))continue;
                boolean eligible=events.stream().anyMatch(e->e.cause().name().equals(t.cause())&&switch(t.relation()){case "self"->e.card().equals(source.token);case "ally"->e.controller()==seat;case "enemy"->e.controller()!=seat;default->true;});
                if(!eligible)continue;if(t.effect().oncePerTurn())used.add(scope);
                chain.add(new Link(source,"",seat,chain.size()+1,t.effect()));note("Chain "+chain.size()+": "+source.card.name()+" triggers.");priority=1-seat;open=false;passes=0;advance=false;
            }
        }
    }
    private void destroyBattle(Piece p,Piece source) {if(protectFromDestruction(p,source,Cause.BATTLE)||hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,0);else if(p.shield>0)p.shield--;else{move(p,Zone.DISCARD,Cause.BATTLE,source.token,0);signal("ON_DESTROY",source,p);}}
    private void nextPhase() {
        signal("ON_PHASE_END",null,null);
        if(phase==Phase.END){signal("ON_TURN_END",null,null);expireEffects();}
        if(phase==Phase.END){turn++;turnPlayer=1-turnPlayer;phase=Phase.DRAW;normal[turnPlayer]=0;used.clear();pieces.values().forEach(p->{p.attacked=false;p.boost=p.permanentBoost+expiries.stream().filter(e->e.token().equals(p.token)&&e.key().equals("BOOST")&&e.endTurn()>=turn).mapToInt(Expiry::value).sum();});tickAdvancedSummons(turnPlayer);draw(turnPlayer);}
        else phase=Phase.values()[phase.ordinal()+1];
        signal("ON_PHASE_START",null,null);if(phase==Phase.DRAW)signal("ON_TURN_START",null,null);
        note("Turn "+turn+" · "+phase);
    }
    private void draw(int actor) {Piece p=pieces.values().stream().filter(x->x.controller==actor&&x.zone==Zone.DECK).findFirst().orElse(null);if(p==null){winner=1-actor;note("A duelist could not draw.");}else move(p,Zone.HAND,Cause.DRAW,"",0);}
    private void damage(int actor,int amount) {
        if(pieces.values().stream().anyMatch(p->p.zone==Zone.FIELD&&p.controller==actor&&hasFlag(p,"PREVENT_DAMAGE"))){cue("SHIELD",null,"",actor,0);return;}
        boolean reflect=pieces.values().stream().anyMatch(p->p.zone==Zone.FIELD&&p.controller==actor&&hasFlag(p,"REFLECT_DAMAGE"));
        int recipient=reflect?1-actor:actor;life[recipient]=Math.max(0,life[recipient]-Math.max(0,amount));
        cue("DAMAGE",pieces.get(attacker),target,recipient,0);if(life[recipient]==0)winner=1-recipient;
    }
    private void move(Piece p,Zone to,Cause cause,String source,int link) {Zone from=p.zone;if((from==Zone.SUPPORT||from==Zone.STADIUM)&&to!=from){faceDown.remove(p.token);setTurn.remove(p.token);}if(from==Zone.FIELD&&to!=Zone.FIELD){SummonFramework.Progress progress=summonProgress.get(p.controller);if(progress!=null&&progress.token().equals(p.token))summonProgress.remove(p.controller);}p.zone=to;p.generation++;p.inspectedBy.clear();if(resolvingContext!=null){var memory=new EffectContext.Selection(p.token,p.generation);if(to==Zone.BANISHED)resolvingContext.banishedThisResolution.add(memory);if(cause==Cause.BATTLE||cause==Cause.DESTROY)resolvingContext.destroyedThisResolution.add(memory);}
        Event event=new Event(history.size()+1,"move",p.token,p.owner,p.controller,from,to,cause,source,link);history.add(event);pendingEvents.add(event);if(to!=Zone.FIELD){p.boost=0;p.shield=0;p.attacked=false;p.position=BattlePosition.ATTACK;p.summonedTurn=-1;p.positionTurn=-1;p.flags.clear();p.fixedPower=-1;p.extraAttacks=0;p.permanentBoost=0;expiries.removeIf(expiry->expiry.token().equals(p.token));p.grantedEffect=null;p.controller=p.owner;}cueMove(p,from,to,cause,source,link);}
    private int count(int actor,Zone zone) {return (int)pieces.values().stream().filter(p->p.controller==actor&&p.zone==zone).count();}
    private void note(String text) {log.add(text);}
    public synchronized long revision(){return revision;}
    public synchronized int winner(){return winner;}
    public synchronized List<Event> history(){return List.copyOf(history);}
    private VisibleCard visibleCard(Piece p,int viewer){
        boolean supportSet=faceDown.contains(p.token);
        boolean monsterSet=p.zone==Zone.FIELD&&p.position==BattlePosition.FACE_DOWN_DEFENSE;
        if((supportSet||monsterSet)&&p.controller!=viewer&&!p.inspectedBy.contains(viewer)){String hiddenCategory=monsterSet?"facedown_pokemon":"facedown";return new VisibleCard(p.token,monsterSet?"Set Pokemon":"Set card","",List.of(),"",hiddenCategory,0,"",p.zone,p.controller,p.position.name());}
        String category=supportSet?"facedown_"+p.card.category():p.card.category();
        return new VisibleCard(p.token,p.card.name(),p.card.species(),p.card.aspects(),p.card.type(),category,power(p),p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p),Map.copyOf(p.counters));
    }
    /** Public piles are ordered by arrival; the last visible card is the actual top card. */
    private List<VisibleCard> orderPublicPiles(List<VisibleCard> cards) {
        Map<String,Long> arrival = new HashMap<>();
        for (Event event : history) if (event.to() == Zone.DISCARD || event.to() == Zone.BANISHED)
            arrival.put(event.card(), event.sequence());
        return cards.stream().sorted(Comparator.comparingLong(c ->
            c.zone() == Zone.DISCARD || c.zone() == Zone.BANISHED ? arrival.getOrDefault(c.token(), 0L) : -1L)).toList();
    }
    public synchronized View view(int viewer) {
        List<VisibleCard> visible=pieces.values().stream().filter(p->(p.zone!=Zone.DECK||p.lookedBy==viewer)&&(p.zone!=Zone.HAND&&p.zone!=Zone.EXTRA||p.controller==viewer||revealed.contains(p.token))).map(p->visibleCard(p,viewer)).toList();
        return new View(revision,viewer,turnPlayer,priority,turn,phase.name(),open,List.of(life[0],life[1]),List.of(count(0,Zone.DECK),count(1,Zone.DECK)),List.of(count(0,Zone.HAND),count(1,Zone.HAND)),List.of(count(0,Zone.EXTRA),count(1,Zone.EXTRA)),orderPublicPiles(visible),
            chain.stream().map(l->"Chain "+l.number+" · "+l.source.card.name()).toList(),winner<0?"":Integer.toString(winner),List.copyOf(log.subList(Math.max(0,log.size()-12),log.size())),visibleCues(viewer));
    }
    public synchronized View spectatorView(){
        List<VisibleCard> visible=pieces.values().stream().filter(p->p.zone!=Zone.DECK&&p.zone!=Zone.HAND&&p.zone!=Zone.EXTRA).map(p->visibleCard(p,-1)).toList();
        return new View(revision,0,turnPlayer,priority,turn,phase.name(),open,List.of(life[0],life[1]),List.of(count(0,Zone.DECK),count(1,Zone.DECK)),List.of(count(0,Zone.HAND),count(1,Zone.HAND)),List.of(count(0,Zone.EXTRA),count(1,Zone.EXTRA)),orderPublicPiles(visible),
            chain.stream().map(l->"Chain "+l.number+" · "+l.source.card.name()).toList(),winner<0?"":Integer.toString(winner),List.copyOf(log.subList(Math.max(0,log.size()-12),log.size())),visibleCues(-1));
    }

    /** Admin/QA-only deterministic Spell/Trap scenario. Production actions still use act(). */
    public synchronized void qaPrepareSpellTrapScenario(int actor){
        qaReset(actor,3);
        qaAdd(actor,"charmander",Zone.FIELD);
        qaAdd(1-actor,"squirtle",Zone.FIELD);
        qaAdd(actor,"flamethrower",Zone.HAND);
        Piece counter=qaAdd(actor,"counter_seal",Zone.SUPPORT);
        Piece mirror=qaAdd(1-actor,"mirror_barrier",Zone.SUPPORT);
        faceDown.add(counter.token);faceDown.add(mirror.token);
        setTurn.put(counter.token,turn-1);setTurn.put(mirror.token,turn-1);
        note("QA Spell/Trap scenario ready: two Set cards from the previous turn.");
        revision++;
    }
    public synchronized void qaOpenSpellTrapChain(int actor){
        require(winner<0,"This duel has ended.");require(turnPlayer==actor&&priority==actor&&open,"QA Spell/Trap scenario is not ready.");
        Piece spell=qaFind(actor,"flamethrower",Zone.HAND);
        Piece mirror=qaFind(1-actor,"mirror_barrier",Zone.SUPPORT);
        Piece counter=qaFind(actor,"counter_seal",Zone.SUPPORT);
        act(actor,new Action("activate",spell.token,""),revision);
        act(1-actor,new Action("activate",mirror.token,"1"),revision);
        act(actor,new Action("activate",counter.token,"2"),revision);
    }
    public synchronized void qaResolveSpellTrapChain(int actor){
        require(!chain.isEmpty(),"Expected an active Spell/Trap chain.");
        int guard=4;
        while(!chain.isEmpty()&&guard-->0)act(priority,new Action("pass","",""),revision);
        require(chain.isEmpty(),"Spell/Trap chain did not resolve.");
    }
    /** Admin/QA-only deterministic Creation summon scenario. */
    public synchronized void qaPrepareCreationScenario(int actor){
        qaReset(actor,5);
        qaAdd(actor,"palkia",Zone.FIELD);
        qaAdd(actor,"dialga",Zone.FIELD);
        qaAdd(actor,"giratina",Zone.FIELD);
        qaAdd(actor,"arceus_defense",Zone.EXTRA);
        qaAdd(actor,"arceus_judgement",Zone.EXTRA);
        qaAdd(actor,"ultimate_arceus",Zone.EXTRA);
        qaAdd(1-actor,"squirtle",Zone.FIELD);
        note("QA Creation scenario ready: Palkia + Dialga + Giratina.");
        revision++;
    }
    public synchronized void qaSummonCreation(int actor){
        require(turnPlayer==actor&&priority==actor&&open,"QA Creation scenario is not ready.");
        Piece result=qaFind(actor,"arceus_defense",Zone.EXTRA);
        String materials=String.join(",",
            qaFind(actor,"palkia",Zone.FIELD).token,
            qaFind(actor,"dialga",Zone.FIELD).token,
            qaFind(actor,"giratina",Zone.FIELD).token);
        act(actor,new Action("play",result.token,materials),revision);
    }
    public synchronized void qaTickCreation(int actor){
        require(summonProgress.containsKey(actor),"No Creation summon is progressing.");
        tickAdvancedSummons(actor);revision++;
    }
    /** Admin/QA-only deterministic monster-position presentation scenario. */
    public synchronized void qaPreparePositionScenario(int actor) {
        qaReset(actor, 6);

        Piece attack = qaAdd(actor, "arceus_defense", Zone.FIELD);
        attack.position = BattlePosition.ATTACK;
        attack.summonedTurn = turn - 1;

        Piece defense = qaAdd(actor, "pikachu", Zone.FIELD);
        defense.position = BattlePosition.DEFENSE;
        defense.summonedTurn = turn - 1;

        Piece setMonster = qaAdd(actor, "squirtle", Zone.HAND);
        normal[actor] = 0;
        setMonster(actor, setMonster.token, "");

        require(setMonster.zone == Zone.FIELD, "QA Normal Set did not move the monster to the field.");
        require(setMonster.position == BattlePosition.FACE_DOWN_DEFENSE, "QA Normal Set did not produce face-down Defense Position.");
        require(!faceDown.contains(setMonster.token), "Monster Set must use battle position, not the Spell/Trap faceDown registry.");

        // Real catalog cards populate all public/hidden pile zones for visual proof.
        for (int seat = 0; seat < 2; seat++) {
            for (int i = 0; i < 12; i++) qaAdd(seat, "charmander", Zone.DECK);
            qaAdd(seat, "arceus_defense", Zone.EXTRA);
            qaAdd(seat, "arceus_judgement", Zone.EXTRA);
            qaAdd(seat, "ultimate_arceus", Zone.EXTRA);
            qaAdd(seat, "protect", Zone.DISCARD);
            qaAdd(seat, "flamethrower", Zone.DISCARD);
            qaAdd(seat, "pikachu", Zone.BANISHED);
        }
        note("QA_PILE_COUNTS_READY: both sides have Deck, Extra Deck, Graveyard and Banished cards.");
        note("QA position scenario ready: Attack Pokemon, face-up Defense Pokemon, and real Normal Set face-down Defense card.");
        revision++;
    }

    void qaReset(int actor,int qaTurn){
        require(actor==0||actor==1,"Choose a duelist seat.");
        stageIndex.clear();delayed.clear();stageUsed.clear();stageDuelUsed.clear();optionalTriggers.clear();continuous.clear();activatedContinuous.clear();expiries.clear();revealed.clear();duelUsed.clear();cues.clear();summonLocks[0]=-1;summonLocks[1]=-1;pieces.clear();chain.clear();history.clear();pendingEvents.clear();log.clear();used.clear();faceDown.clear();setTurn.clear();summonProgress.clear();
        life[0]=catalog.rules().life();life[1]=catalog.rules().life();normal[0]=0;normal[1]=0;
        turnPlayer=actor;priority=actor;turn=qaTurn;passes=0;winner=-1;phase=Phase.MAIN1;open=true;advance=false;attacker=null;target=null;
    }
    Piece qaAdd(int actor,String cardId,Zone zone){
        String token=UUID.randomUUID().toString();Piece piece=new Piece(token,catalog.card(cardId),actor,zone);pieces.put(token,piece);return piece;
    }
    private Piece qaFind(int actor,String cardId,Zone zone){
        return pieces.values().stream().filter(p->p.controller==actor&&p.card.id().equals(cardId)&&p.zone==zone).findFirst()
            .orElseThrow(()->new IllegalArgumentException("QA card missing: "+cardId+" in "+zone));
    }

    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}

    public record Cue(long sequence,String semantic,String source,String target,int controller,String element,
                      vn.svarcade.tcg.data.EffectSpec.Presentation presentation,int link) {}
    private final List<Cue> cues=new ArrayList<>();
    private long cueSequence;
    private final Set<String> duelUsed=new HashSet<>();
    private final Map<String,vn.svarcade.tcg.data.EffectSpec> continuous=new LinkedHashMap<>();
    private final Set<String> activatedContinuous=new HashSet<>();
    private record Expiry(String token,String key,int value,int endTurn,int oldController,Catalog.Effect oldEffect) {}
    private final List<Expiry> expiries=new ArrayList<>();
    private final Set<String> revealed=new HashSet<>();
    private final Random effectRandom=new Random(0x43415244);
    private final int[] summonLocks={-1,-1};
    private final Map<String,Event> optionalTriggers=new HashMap<>();
    private Event conditionEvent;
    private boolean powerCondition;
    public synchronized int registeredEffects() { syncContinuous();return continuous.size(); }
    public static int effectPrimitiveCount() { return vn.svarcade.tcg.data.EffectSpec.OPERATIONS.size()-1; }
    private void cue(String semantic,Piece source,String target,int actor,int link) {
        var presentation=source==null?null:Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL","CHARGE","DIRECT_ATTACK","IMPACT").contains(semantic)?vn.svarcade.tcg.data.EffectContent.attackPresentation(source.card):vn.svarcade.tcg.data.EffectContent.presentation(source.card);
        cues.add(new Cue(++cueSequence,semantic,source==null?"":source.token,target==null?"":target,actor,
            source==null?"normal":source.card.type(),presentation,link));
        if(cues.size()>96)cues.removeFirst();
    }
    private List<Cue> visibleCues(int viewer) {
        return cues.stream().map(c->{
            Piece source=pieces.get(c.source()),target=pieces.get(c.target());
            boolean hidden=source!=null&&(source.zone==Zone.DECK||source.zone==Zone.HAND||source.zone==Zone.EXTRA
                ||faceDown.contains(source.token)||source.position==BattlePosition.FACE_DOWN_DEFENSE);
            boolean hiddenTarget=target!=null&&(target.zone==Zone.DECK||target.zone==Zone.HAND||target.zone==Zone.EXTRA);
            return new Cue(c.sequence(),c.semantic(),hidden?"":c.source(),hiddenTarget?"":c.target(),c.controller(),
                hidden?"normal":c.element(),hidden?null:c.presentation(),c.link());
        }).toList();
    }
    private void signal(String kind,Piece source,Piece targetPiece) {
        Event event=new Event(history.size()+1,kind,source==null?"":source.token,source==null?turnPlayer:source.owner,
            source==null?turnPlayer:source.controller,source==null?Zone.FIELD:source.zone,source==null?Zone.FIELD:source.zone,
            Cause.SYSTEM,targetPiece==null?(kind.contains("TURN")||kind.contains("PHASE")?phase.name():""):targetPiece.token,chain.size());
        pendingEvents.add(event);
    }
    private void cueMove(Piece p,Zone from,Zone to,Cause cause,String source,int link) {
        String semantic=switch(to) {
            case FIELD -> switch(cause) {
                case PLAY -> "SUMMON_NORMAL";case TRIBUTE_SUMMON -> "SUMMON_TRIBUTE";
                case EXTRA_SUMMON,SPECIAL_SUMMON -> "SUMMON_EXTRA";case EVOLVE -> "SUMMON_EVOLUTION";
                case SUMMON_STAGE -> "SUMMON_TRANSFORM";case REVIVE -> "REVIVE";default -> "POSITION_CHANGE";
            };
            case DISCARD -> switch(cause) {case BATTLE,DESTROY -> "DESTROY";case DRAW -> "MILL";case DISCARD -> "DISCARD";default -> "SEND_GRAVE";};
            case BANISHED -> "BANISH";case HAND -> cause==Cause.DRAW?"DRAW":"RETURN_HAND";
            case DECK,EXTRA -> "RETURN_DECK";case SUPPORT,STADIUM -> "POSITION_CHANGE";
        };
        cue(semantic,p,source,p.controller,link);
        syncContinuous();
    }
    private Catalog.Effect effectiveEffect(Piece p) { return p.flags.contains("EFFECT_REMOVED")?null:p.grantedEffect==null?p.card.effect():p.grantedEffect; }
    private void syncContinuous() {
        Set<String> live=new HashSet<>();
        for(Piece p:pieces.values()) {
            Catalog.Effect e=effectiveEffect(p);
            if(e!=null&&e.spec()!=null&&e.spec().triggers().contains("CONTINUOUS")&&(p.card.category().equals("pokemon")||activatedContinuous.contains(p.token))
                &&(p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM)&&!faceDown.contains(p.token)
                &&p.position!=BattlePosition.FACE_DOWN_DEFENSE&&!p.flags.contains("CANNOT_ACTIVATE")) {
                live.add(p.token);continuous.put(p.token,e.spec());
            }
        }
        continuous.keySet().retainAll(live);
        activatedContinuous.removeIf(token->{Piece p=pieces.get(token);return p==null||p.zone!=Zone.FIELD&&p.zone!=Zone.SUPPORT&&p.zone!=Zone.STADIUM;});
    }
    private boolean filtered(Piece p,vn.svarcade.tcg.data.EffectSpec.Filter f,int actor) {
        if(f==null)return true;
        if(f.category()!=null&&!f.category().equals(p.card.category()))return false;
        if(!vn.svarcade.tcg.data.EffectSpec.list(f.types()).isEmpty()&&!f.types().contains(p.card.type()))return false;
        if(!vn.svarcade.tcg.data.EffectSpec.list(f.tags()).isEmpty()&&f.tags().stream().noneMatch(t->p.card.family().equals(t)||p.card.aspects().contains(t)||p.card.sources().contains(t)))return false;
        if(p.card.level()<f.minLevel()||f.maxLevel()>0&&p.card.level()>f.maxLevel())return false;
        if(p.card.power()<f.minPower()||f.maxPower()>0&&p.card.power()>f.maxPower())return false;
        if(f.position()!=null&&!f.position().equals(p.position.name()))return false;
        if(f.zone()!=null&&!f.zone().equals(p.zone.name()))return false;
        if(f.controller()!=null&&(f.controller().equals("ALLY")?p.controller!=actor:p.controller==actor))return false;
        return f.faceUp()==null||f.faceUp()==!(faceDown.contains(p.token)||p.position==BattlePosition.FACE_DOWN_DEFENSE);
    }
    private List<Piece> select(String selector,Piece source,String selected,int actor,vn.svarcade.tcg.data.EffectSpec.Filter filter) {
        String s=vn.svarcade.tcg.data.EffectSpec.value(selector,"TARGET");
        List<Piece> list=new ArrayList<>();
        Piece chosen=pieces.get(selected);
        if(s.equals("EVENT_CARD")||s.equals("EVENT_TARGET")) {String token=conditionEvent==null?"":s.equals("EVENT_CARD")?conditionEvent.card():conditionEvent.source();Piece p=pieces.get(token);if(p!=null)list.add(p);}
        else if(Set.of("PAID_COSTS","DESTROYED_THIS_RESOLUTION","BANISHED_THIS_RESOLUTION","REMEMBERED").contains(s)) {
            if(resolvingContext!=null){var remembered=switch(s){case "PAID_COSTS"->resolvingContext.paidCosts;case "DESTROYED_THIS_RESOLUTION"->resolvingContext.destroyedThisResolution;case "BANISHED_THIS_RESOLUTION"->resolvingContext.banishedThisResolution;default->resolvingContext.selectedCards.getOrDefault(resolvingContext.flags.getOrDefault("memory","selected"),List.of());};
                for(var memory:remembered){Piece p=pieces.get(memory.token());if(p!=null&&p.generation==memory.generation())list.add(p);}}
        }
        else if(s.equals("PREVIOUS_CHAIN_SOURCE")||s.equals("PREVIOUS_CHAIN_TARGET")){int i=resolvingContext==null?chain.size()-1:resolvingContext.chainLink-2;if(i>=0&&i<chain.size()){var previous=chain.get(i);Piece p=s.endsWith("SOURCE")?previous.source:pieces.get(previous.target);if(p!=null)list.add(p);}}
        else if(s.equals("SELF")||s.equals("SOURCE")){if(source!=null)list.add(source);}
        else if(s.equals("TARGET")){if(chosen!=null)list.add(chosen);}
        else if(s.equals("ATTACKER")){if(pieces.get(attacker)!=null)list.add(pieces.get(attacker));}
        else if(s.equals("DEFENDER")){if(pieces.get(target)!=null)list.add(pieces.get(target));}
        else if(s.equals("CHAIN_SOURCE")||s.equals("CHAIN_TARGET")){
            int i=chain.size()-1;try{i=Integer.parseInt(selected)-1;}catch(Exception ignored){}
            if(i>=0&&i<chain.size()){Link l=chain.get(i);Piece p=s.equals("CHAIN_SOURCE")?l.source:pieces.get(l.target);if(p!=null)list.add(p);}
        } else for(Piece p:pieces.values()) {
            boolean yes=switch(s){
                case "ALLY_MONSTER" -> p.zone==Zone.FIELD&&p.controller==actor;
                case "ENEMY_MONSTER" -> p.zone==Zone.FIELD&&p.controller!=actor;
                case "ANY_MONSTER","ALL_FIELD" -> p.zone==Zone.FIELD;
                case "ALLY_CARD" -> (p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM)&&p.controller==actor;
                case "ENEMY_CARD" -> (p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM)&&p.controller!=actor;
                case "ANY_FIELD_CARD" -> p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM;
                case "ALL_ALLIES" -> p.zone==Zone.FIELD&&p.controller==actor;
                case "ALL_ENEMIES" -> p.zone==Zone.FIELD&&p.controller!=actor;
                case "HAND","RANDOM_HAND" -> p.zone==Zone.HAND&&p.controller==actor;
                case "GRAVEYARD" -> p.zone==Zone.DISCARD&&p.owner==actor;
                case "BANISHED" -> p.zone==Zone.BANISHED&&p.owner==actor;
                case "DECK","TOP_DECK","BOTTOM_DECK" -> p.zone==Zone.DECK&&p.controller==actor;
                case "EXTRA_DECK" -> p.zone==Zone.EXTRA&&p.owner==actor;
                default -> false;
            };if(yes)list.add(p);
        }
        list.removeIf(p->!filtered(p,filter,actor));
        if(chosen!=null&&list.contains(chosen)&&!s.startsWith("ALL_")){list.clear();list.add(chosen);}
        if(s.equals("BOTTOM_DECK"))Collections.reverse(list);
        if(s.equals("RANDOM_HAND"))Collections.shuffle(list,effectRandom);
        return list;
    }
    private boolean condition(vn.svarcade.tcg.data.EffectSpec.Condition c,Piece source,String selected,int actor) {
        if(c==null)return true;
        var children=vn.svarcade.tcg.data.EffectSpec.list(c.children());String value=vn.svarcade.tcg.data.EffectSpec.value(c.value(),"");
        List<Piece> targets=select(c.target(),source,selected,actor,null);
        return switch(c.type()) {
            case "AND" -> children.stream().allMatch(k->condition(k,source,selected,actor));
            case "OR" -> children.stream().anyMatch(k->condition(k,source,selected,actor));
            case "NOT" -> children.size()==1&&!condition(children.getFirst(),source,selected,actor);
            case "TURN_OWNER" -> value.equals("ALLY")?actor==(conditionEvent==null?turnPlayer:conditionEvent.controller()):actor!=(conditionEvent==null?turnPlayer:conditionEvent.controller());
            case "EVENT_CONTROLLER" -> conditionEvent!=null&&(value.equals("ENEMY")?conditionEvent.controller()!=actor:conditionEvent.controller()==actor);
            case "PHASE" -> value.equals(conditionEvent!=null&&(conditionEvent.kind().contains("TURN")||conditionEvent.kind().contains("PHASE"))?conditionEvent.source():phase.name());
            case "LP_AT_MOST" -> life[value.equals("ENEMY")?1-actor:actor]<=c.amount();
            case "LP_AT_LEAST" -> life[value.equals("ENEMY")?1-actor:actor]>=c.amount();
            case "CHAIN_AT_LEAST" -> chain.size()>=c.amount();
            case "COUNT_AT_LEAST" -> targets.size()>=c.amount();
            case "COUNT_AT_MOST" -> targets.size()<=c.amount();
            case "COUNTER_AT_MOST" -> targets.stream().anyMatch(p->p.counters.getOrDefault(value,0)<=c.amount());
            case "SEEN_BY_CONTROLLER" -> targets.stream().anyMatch(p->p.inspectedBy.contains(actor));
            case "EVENT_TYPE" -> conditionEvent!=null&&pieces.containsKey(conditionEvent.card())&&pieces.get(conditionEvent.card()).card.category().equals(value);
            case "NOT_SELF" -> targets.stream().anyMatch(p->p!=source);
            case "SURVIVED" -> targets.stream().anyMatch(p->p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM);
            case "COUNTER_AT_LEAST" -> targets.stream().anyMatch(p->p.counters.getOrDefault(value,0)>=c.amount());
            case "SOURCE_ZONE" -> source!=null&&source.zone.name().equals(value);
            case "TARGET_ZONE" -> targets.stream().anyMatch(p->p.zone.name().equals(value));
            case "CATEGORY" -> targets.stream().anyMatch(p->p.card.category().equals(value));
            case "TYPE" -> targets.stream().anyMatch(p->p.card.type().equals(value));
            case "TAG" -> targets.stream().anyMatch(p->p.card.family().equals(value)||p.card.aspects().contains(value));
            case "LEVEL_AT_LEAST" -> targets.stream().anyMatch(p->p.card.level()>=c.amount());
            case "POWER_AT_LEAST" -> targets.stream().anyMatch(p->(powerCondition?p.card.power()+p.boost:power(p))>=c.amount());
            case "POSITION" -> targets.stream().anyMatch(p->p.position.name().equals(value));
            case "FACE_UP" -> targets.stream().anyMatch(p->p.position!=BattlePosition.FACE_DOWN_DEFENSE&&!faceDown.contains(p.token));
            case "FACE_DOWN" -> targets.stream().anyMatch(p->p.position==BattlePosition.FACE_DOWN_DEFENSE||faceDown.contains(p.token));
            case "DESTROYED_BY_BATTLE","DESTROYED_BY_EFFECT" -> history.stream().anyMatch(e->source!=null&&e.card().equals(source.token)&&e.cause()==(c.type().endsWith("BATTLE")?Cause.BATTLE:Cause.DESTROY));
            default -> false;
        };
    }
    private record Payment(vn.svarcade.tcg.data.EffectSpec.Cost cost,List<Piece> cards) {}
    private List<Payment> validateSpec(Piece p,Catalog.Effect e,String selected,int actor) {
        require(!hasFlag(p,"CANNOT_ACTIVATE"),"cardworlds.error.cannot_activate");
        var spec=e.spec();if(spec==null)return List.of();
        require(!spec.triggers().contains("CONTINUOUS")||!activatedContinuous.contains(p.token),"cardworlds.error.already_registered");
        require(!spec.oncePerDuel()||!duelUsed.contains(limitKey(p,e,validatingStage)),"cardworlds.error.once_per_duel");
        for(var c:vn.svarcade.tcg.data.EffectSpec.list(spec.conditions()))require(condition(c,p,selected,actor),"cardworlds.error.condition");
        if(spec.targets()!=null&&spec.targets().min()>0) {
            var targets=select(spec.targets().selector(),p,selected,actor,spec.targets().filter());
            require(targets.size()>=spec.targets().min(),"cardworlds.error.target");
            for(Piece targetPiece:targets)require(!hasFlag(targetPiece,"PREVENT_TARGET")||targetPiece.controller==actor,"cardworlds.error.protected_target");
        }
        List<Payment> plan=new ArrayList<>();Set<String> reserved=new HashSet<>();Map<String,Integer> counterReservations=new HashMap<>();int lp=e.lifeCost();
        for(var c:vn.svarcade.tcg.data.EffectSpec.list(spec.costs())) {
            if(c.type().equals("COUNTER_COST")) {int needed=counterReservations.merge(c.counter(),c.amount(),Integer::sum);
                require(p.counters.getOrDefault(c.counter(),0)>=needed,"cardworlds.error.cost");plan.add(new Payment(c,List.of(p)));continue;}
            if(c.type().equals("LP_COST")){lp+=c.amount();plan.add(new Payment(c,List.of()));continue;}
            if(c.type().equals("LOCK_AFTER_USE")){plan.add(new Payment(c,List.of(p)));continue;}
            String selector=switch(c.type()) {case "BANISH_SELF","RETURN_SELF" -> "SELF";case "DISCARD","REVEAL_HAND" -> "HAND";case "TRIBUTE" -> "ALLY_MONSTER";case "BANISH_GRAVE" -> "GRAVEYARD";default -> vn.svarcade.tcg.data.EffectSpec.value(c.target(),"TARGET");};
            var pool=new ArrayList<>(select(selector,p,"",actor,c.filter()));pool.removeIf(t->reserved.contains(t.token)||c.type().equals("DISCARD")&&p.zone==Zone.HAND&&t==p||c.type().equals("BANISH_GRAVE")&&t.token.equals(selected));
            int n=Math.max(1,c.amount());require(pool.size()>=n,"cardworlds.error.cost");
            List<Piece> chosen=List.copyOf(pool.subList(0,n));
            if(!c.type().equals("REVEAL_HAND"))chosen.forEach(t->reserved.add(t.token));plan.add(new Payment(c,chosen));
        }
        require(life[actor]>lp,"cardworlds.error.life_cost");return plan;
    }
    private void paySpec(Piece source,Catalog.Effect e,List<Payment> plan,int actor) {
        if(e.spec()==null)return;
        if(e.spec().oncePerDuel())duelUsed.add(limitKey(source,e,validatingStage));
        for(Payment payment:plan) {
            var c=payment.cost();
            if(c.type().equals("LP_COST")){life[actor]-=c.amount();continue;}
            for(Piece p:payment.cards())switch(c.type()) {
                case "COUNTER_COST" -> {p.counters.compute(c.counter(),(k,n)->n-c.amount());cue("CHARGE",source,p.token,actor,0);}
                case "DISCARD" -> move(p,Zone.DISCARD,Cause.DISCARD,source.token,0);
                case "TRIBUTE" -> move(p,Zone.DISCARD,Cause.TRIBUTE,source.token,0);
                case "SEND_GRAVE" -> move(p,Zone.DISCARD,Cause.EFFECT,source.token,0);
                case "BANISH_SELF","BANISH_GRAVE" -> move(p,Zone.BANISHED,Cause.BANISH,source.token,0);
                case "RETURN_SELF","RETURN_OTHER" -> move(p,p.card.extra()?Zone.EXTRA:Zone.HAND,Cause.RETURN,source.token,0);
                case "REVEAL_HAND" -> {revealed.add(p.token);cue("SEARCH",p,"",actor,0);}
                case "LOCK_AFTER_USE" -> addFlag(p,"CANNOT_ACTIVATE","TURN_END");
                default -> throw new IllegalStateException("Unvalidated cost");
            }
        }
    }
    private String flag(vn.svarcade.tcg.data.EffectSpec.Operation op,String key,String fallback) {
        return op.flags()==null?fallback:op.flags().getOrDefault(key,fallback);
    }
    private boolean hasFlag(Piece p,String key) {
        if(p.flags.contains(key))return true;
        for(var entry:continuous.entrySet()) {
            Piece source=pieces.get(entry.getKey());if(source==null)continue;
            for(var op:entry.getValue().operations())if(op.type().equals(key)
                &&condition(op.condition(),source,p.token,source.controller)&&select(op.target(),source,p.token,source.controller,op.filter()!=null?op.filter():entry.getValue().targets()==null?null:entry.getValue().targets().filter()).contains(p))return true;
        }
        return false;
    }
    private void addFlag(Piece p,String key,String duration) {
        if(p.flags.add(key)&&!vn.svarcade.tcg.data.EffectSpec.value(duration,"PERMANENT").equals("PERMANENT"))
            expiries.add(new Expiry(p.token,key,0,expiryTurn(duration),p.controller,null));
    }
    private int expiryTurn(String duration) {
        if(duration!=null&&duration.startsWith("TURNS:"))return turn+Math.clamp(Integer.parseInt(duration.substring(6))-1,0,20);
        return "NEXT_TURN_END".equals(duration)?turn+1:turn;
    }
    private int compositePower(Piece p,int value) {
        if(powerCondition)return Math.max(0,value);powerCondition=true;try {
        if(p.fixedPower>=0)value=p.fixedPower;
        for(var entry:continuous.entrySet()) {
            Piece source=pieces.get(entry.getKey());if(source==null)continue;
            for(var op:entry.getValue().operations())if(op.type().equals("MODIFY_POWER")
                &&condition(op.condition(),source,p.token,source.controller)&&select(op.target(),source,p.token,source.controller,op.filter()!=null?op.filter():entry.getValue().targets()==null?null:entry.getValue().targets().filter()).contains(p))value+=op.amount();
        }
        return Math.max(0,value);
        }finally{powerCondition=false;}
    }
    private void expireEffects() {
        for(var it=expiries.iterator();it.hasNext();) {
            var expiry=it.next();if(expiry.endTurn()>turn)continue;Piece p=pieces.get(expiry.token());
            if(p!=null&&(p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM))switch(expiry.key()) {
                case "BOOST" -> p.boost-=expiry.value();
                case "SET_POWER" -> p.fixedPower=expiry.value();
                case "CONTROL" -> p.controller=expiry.oldController();
                case "EFFECT" -> p.grantedEffect=expiry.oldEffect();
                default -> p.flags.remove(expiry.key());
            }
            it.remove();
        }
        optionalTriggers.clear();revealed.clear();pieces.values().forEach(p->{p.extraAttacks=0;p.lookedBy=-1;});syncContinuous();
    }
    private void reorderDeck(int actor,List<Piece> order) {
        List<Piece> others=pieces.values().stream().filter(p->!order.contains(p)).toList();
        // Reinsert only deck cards; board/hand order and every stable token stay unchanged.
        List<Piece> restDeck=others.stream().filter(p->p.controller==actor&&p.zone==Zone.DECK).toList();
        pieces.entrySet().removeIf(e->e.getValue().controller==actor&&e.getValue().zone==Zone.DECK);
        order.forEach(p->pieces.put(p.token,p));restDeck.forEach(p->pieces.put(p.token,p));
    }
    private void runOperations(Link l,List<vn.svarcade.tcg.data.EffectSpec.Operation> operations,int depth) {
        if(depth>8)return;
        var spec=l.effect.spec();Piece source=l.source;
        for(var op:operations) {
            if(!condition(op.condition(),source,l.target,l.actor)){runOperations(l,vn.svarcade.tcg.data.EffectSpec.list(op.otherwise()),depth+1);continue;}
            if(op.type().equals("DELAY")){schedule(l,op);continue;}
            if(op.flags()!=null&&op.flags().containsKey("memory"))l.context.flags.put("memory",op.flags().get("memory"));
            if(op.type().equals("IF")){runOperations(l,vn.svarcade.tcg.data.EffectSpec.list(op.children()),depth+1);continue;}
            int selectorActor=flag(op,"controller","ALLY").equals("ENEMY")?1-l.actor:l.actor;
            List<Piece> candidates=select(op.target(),source,l.target,selectorActor,op.filter()!=null?op.filter():spec.targets()==null?null:spec.targets().filter());
            if(Set.of("REVIVE","SPECIAL_SUMMON","SUMMON_FROM_HAND","SUMMON_FROM_GRAVE","SUMMON_FROM_BANISHED").contains(op.type()))candidates.removeIf(p->!p.card.category().equals("pokemon")||p.card.extra()&&!flag(op,"allowExtra","false").equals("true"));
            int limit=op.target()!=null&&op.target().startsWith("ALL_")?candidates.size():Math.max(1,Integer.parseInt(flag(op,"count","1")));
            List<Piece> chosen=candidates.stream().limit(limit).toList();
            int recipient=flag(op,"recipient","ALLY").equals("ENEMY")?1-l.actor:l.actor;
            switch(op.type()) {
                case "DAMAGE_LP" -> {damage(recipient,Math.max(0,op.amount()));cue("DAMAGE",source,l.target,recipient,l.number);signal("ON_DAMAGE",source,pieces.get(l.target));}
                case "HEAL_LP" -> {life[recipient]=Math.min(100000,life[recipient]+Math.max(0,op.amount()));cue("HEAL",source,l.target,recipient,l.number);}
                case "DRAW" -> {for(int i=0;i<Math.min(20,op.amount())&&winner<0;i++)draw(recipient);}
                case "SEARCH" -> {for(Piece p:candidates.stream().limit(Math.max(1,op.amount())).toList())if(p.zone==Zone.DECK){move(p,Zone.HAND,Cause.EFFECT,source.token,l.number);if(Boolean.parseBoolean(flag(op,"reveal","true")))revealed.add(p.token);cue("SEARCH",source,"",l.actor,l.number);}}
                case "REMEMBER" -> l.context.remember(flag(op,"memory","selected"),chosen.stream().map(p->new EffectContext.Selection(p.token,p.generation)).toList());
                case "REPLACE_DESTROY" -> {l.context.flags.put("prevent_destroy","true");cue("SHIELD",source,l.target,l.actor,l.number);}
                case "INSPECT_SET" -> {for(Piece p:chosen)if(faceDown.contains(p.token)&&p.controller!=l.actor){p.inspectedBy.add(l.actor);cue("SEARCH",source,p.token,l.actor,l.number);}}
                case "SHUFFLE" -> {var deck=new ArrayList<>(select("DECK",source,"",recipient,null));Collections.shuffle(deck,effectRandom);reorderDeck(recipient,deck);}
                case "MILL" -> {for(Piece p:select("TOP_DECK",source,"",recipient,null).stream().limit(Math.max(0,op.amount())).toList()){move(p,Zone.DISCARD,Cause.DRAW,source.token,l.number);}}
                case "LOOK_TOP_DECK" -> {select("TOP_DECK",source,"",l.actor,null).stream().limit(Math.max(0,op.amount())).forEach(p->p.lookedBy=l.actor);cue("SEARCH",source,"",l.actor,l.number);}
                case "REORDER_DECK" -> {var deck=new ArrayList<>(select("TOP_DECK",source,"",l.actor,null).stream().limit(Math.max(1,op.amount())).toList());switch(flag(op,"order","REVERSE")){case "POWER_ASC" -> deck.sort(Comparator.comparingInt(p->p.card.power()));case "POWER_DESC" -> deck.sort(Comparator.comparingInt((Piece p)->p.card.power()).reversed());default -> Collections.reverse(deck);}reorderDeck(l.actor,deck);}
                case "NEGATE_EFFECT","NEGATE_ACTIVATION" -> {int n=l.target.matches("[0-9]+")?Integer.parseInt(l.target)-1:l.number-2;if(n>=0&&n<l.number-1){Link victim=chain.get(n);if(op.type().equals("NEGATE_EFFECT"))victim.negated=true;else victim.activationNegated=true;cue("CHAIN_NEGATE",source,victim.source.token,l.actor,victim.number);signal("ON_NEGATE",source,victim.source);}}
                case "NEGATE_ATTACK" -> {if(attacker!=null){cue("CHAIN_NEGATE",source,attacker,l.actor,l.number);attacker=null;target=null;}}
                case "CANNOT_SUMMON" -> {int player=op.target()!=null&&op.target().equals("ALL_ENEMIES")?1-l.actor:chosen.isEmpty()?l.actor:chosen.getFirst().controller;summonLocks[player]=expiryTurn(op.duration());cue("DEBUFF",source,"",player,l.number);}
                case "REDIRECT_ATTACK" -> {if(!chosen.isEmpty()&&attacker!=null&&chosen.getFirst().controller!=pieces.get(attacker).controller)target=chosen.getFirst().token;}
                case "REDIRECT_TARGET" -> {int n=l.number-2;if(n>=0&&!chosen.isEmpty()){chain.get(n).target=chosen.getFirst().token;chain.get(n).targetGeneration=chosen.getFirst().generation;}}
                default -> {
                    for(Piece p:chosen) {
                        switch(op.type()) {
                            case "MODIFY_POWER" -> {p.boost+=op.amount();if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"BOOST",op.amount(),expiryTurn(op.duration()),p.controller,null));else p.permanentBoost+=op.amount();cue(op.amount()>=0?"BUFF":"DEBUFF",source,p.token,l.actor,l.number);}
                            case "SET_POWER" -> {int old=p.fixedPower;p.fixedPower=Math.max(0,op.amount());if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"SET_POWER",old,expiryTurn(op.duration()),p.controller,null));cue("BUFF",source,p.token,l.actor,l.number);}
                            case "SWAP_POWER" -> {int a=power(source),b=power(p);if(op.duration()!=null&&!op.duration().equals("PERMANENT")){expiries.add(new Expiry(source.token,"SET_POWER",source.fixedPower,expiryTurn(op.duration()),source.controller,null));expiries.add(new Expiry(p.token,"SET_POWER",p.fixedPower,expiryTurn(op.duration()),p.controller,null));}source.fixedPower=b;p.fixedPower=a;cue("BUFF",source,p.token,l.actor,l.number);}
                            case "DESTROY" -> {if(protectFromDestruction(p,source,Cause.DESTROY)||hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,l.number);else if(p.shield>0){p.shield--;cue("SHIELD",p,source.token,p.controller,l.number);}else{move(p,Zone.DISCARD,Cause.DESTROY,source.token,l.number);signal("ON_DESTROY",source,p);}}
                            case "SEND_GRAVE","DISCARD" -> move(p,Zone.DISCARD,op.type().equals("DISCARD")?Cause.DISCARD:Cause.EFFECT,source.token,l.number);
                            case "BANISH" -> move(p,Zone.BANISHED,Cause.BANISH,source.token,l.number);
                            case "RETURN_HAND" -> move(p,p.card.extra()?Zone.EXTRA:Zone.HAND,Cause.RETURN,source.token,l.number);
                            case "RETURN_DECK" -> {move(p,p.card.extra()?Zone.EXTRA:Zone.DECK,Cause.RETURN,source.token,l.number);if(!p.card.extra()&&flag(op,"placement","BOTTOM").equals("TOP"))reorderDeck(p.controller,List.of(p));}
                            case "REVIVE","SPECIAL_SUMMON","SUMMON_FROM_HAND","SUMMON_FROM_GRAVE","SUMMON_FROM_BANISHED" -> {
                                Zone required=switch(op.type()){case "REVIVE","SUMMON_FROM_GRAVE" -> Zone.DISCARD;case "SUMMON_FROM_HAND" -> Zone.HAND;case "SUMMON_FROM_BANISHED" -> Zone.BANISHED;default -> p.zone;};
                                if(p.zone==required&&p.card.category().equals("pokemon")&&count(l.actor,Zone.FIELD)<catalog.rules().pokemonZones()&&!hasFlag(source,"CANNOT_SUMMON")){
                                    if(summonLocks[l.actor]>=turn)break;p.controller=l.actor;move(p,Zone.FIELD,op.type().equals("REVIVE")?Cause.REVIVE:Cause.SPECIAL_SUMMON,source.token,l.number);p.position=BattlePosition.ATTACK;p.summonedTurn=turn;p.positionTurn=turn;
                                }
                            }
                            case "CHANGE_POSITION","FLIP_FACE_UP","SET_FACE_DOWN" -> {if(p.zone==Zone.FIELD){BattlePosition old=p.position;p.position=op.type().equals("SET_FACE_DOWN")?BattlePosition.FACE_DOWN_DEFENSE:op.type().equals("FLIP_FACE_UP")?BattlePosition.ATTACK:BattlePosition.valueOf(flag(op,"position",p.position==BattlePosition.ATTACK?"DEFENSE":"ATTACK"));cue("POSITION_CHANGE",p,"",p.controller,l.number);signal("ON_POSITION_CHANGE",p,null);if(old==BattlePosition.FACE_DOWN_DEFENSE&&p.position!=old)signal("ON_FLIP",p,null);}}
                            case "PREVENT_DESTROY","PREVENT_TARGET","PREVENT_DAMAGE","CANNOT_ATTACK","CANNOT_ACTIVATE","CANNOT_SUMMON","PIERCE","REFLECT_DAMAGE" -> {addFlag(p,op.type(),op.duration());cue(op.type().startsWith("PREVENT")?"SHIELD":"DEBUFF",source,p.token,l.actor,l.number);}
                            case "EXTRA_ATTACK" -> p.extraAttacks+=Math.max(1,op.amount());
                            case "CONTROL_CHANGE" -> {if(p.zone==Zone.FIELD&&count(l.actor,Zone.FIELD)<catalog.rules().pokemonZones()){int old=p.controller;p.controller=l.actor;if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"CONTROL",0,expiryTurn(op.duration()),old,null));cue("CONTROL_CHANGE",source,p.token,l.actor,l.number);}}
                            case "COPY_EFFECT","GRANT_EFFECT" -> {Piece recipientPiece=op.type().equals("COPY_EFFECT")?source:p;Piece donor=op.type().equals("COPY_EFFECT")?p:source;Catalog.Effect old=recipientPiece.grantedEffect;recipientPiece.grantedEffect=effectiveEffect(donor);if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(recipientPiece.token,"EFFECT",0,expiryTurn(op.duration()),recipientPiece.controller,old));}
                            case "REMOVE_EFFECT" -> addFlag(p,"EFFECT_REMOVED",op.duration());
                            case "ADD_COUNTER" -> {String counter=flag(op,"counter","charge");p.counters.compute(counter,(k,n)->Math.min(Integer.parseInt(flag(op,"max","99")),(n==null?0:n)+Math.max(0,op.amount())));cue("CHARGE",source,p.token,l.actor,l.number);}
                            case "REMOVE_COUNTER" -> {p.counters.compute(flag(op,"counter","charge"),(k,n)->Math.max(0,(n==null?0:n)-Math.max(0,op.amount())));cue("CHARGE",source,p.token,l.actor,l.number);}
                            case "REVEAL" -> {revealed.add(p.token);p.lookedBy=l.actor;}
                            case "MOVE_ZONE" -> {Zone zone=Zone.valueOf(op.zone());if(zone!=Zone.FIELD||count(p.controller,Zone.FIELD)<catalog.rules().pokemonZones())move(p,zone,Cause.EFFECT,source.token,l.number);}
                            case "APPLY_STATUS" -> {addFlag(p,flag(op,"status","CANNOT_ATTACK"),op.duration());cue("DEBUFF",source,p.token,l.actor,l.number);}
                            case "REMOVE_STATUS" -> {if(flag(op,"status","ALL").equals("ALL"))p.flags.removeIf(f->f.startsWith("CANNOT_")||f.equals("EFFECT_REMOVED"));else p.flags.remove(flag(op,"status","CANNOT_ATTACK"));cue("HEAL",source,p.token,l.actor,l.number);}
                            default -> throw new IllegalStateException("Unhandled effect primitive " + op.type());
                        }
                    }
                }
            }
            if(!op.type().equals("IF"))runOperations(l,vn.svarcade.tcg.data.EffectSpec.list(op.children()),depth+1);
        }
        syncContinuous();
    }
    private boolean triggerMatches(String trigger,Event event,Piece source) {
        if(trigger.equals(event.kind()))return event.card().isBlank()||event.card().equals(source.token)||source.card.category().equals("reaction")||trigger.equals("ON_CHAIN")||trigger.equals("ON_TURN_START")||trigger.equals("ON_TURN_END")||trigger.startsWith("ON_PHASE");
        if(!event.card().equals(source.token)&&!source.card.category().equals("reaction"))return false;
        return switch(trigger) {
            case "ON_SUMMON" -> event.to()==Zone.FIELD&&Set.of(Cause.PLAY,Cause.TRIBUTE_SUMMON,Cause.EVOLVE).contains(event.cause());
            case "ON_SPECIAL_SUMMON" -> event.to()==Zone.FIELD&&Set.of(Cause.SPECIAL_SUMMON,Cause.EXTRA_SUMMON,Cause.REVIVE,Cause.SUMMON_STAGE).contains(event.cause());
            case "ON_DESTROYED" -> event.to()==Zone.DISCARD&&(event.cause()==Cause.BATTLE||event.cause()==Cause.DESTROY);
            case "ON_SEND_GRAVE" -> event.to()==Zone.DISCARD;
            case "ON_BANISH" -> event.to()==Zone.BANISHED;
            case "ON_RETURN" -> event.cause()==Cause.RETURN;
            case "ON_DRAW" -> event.to()==Zone.HAND&&event.cause()==Cause.DRAW;
            case "ON_DISCARD" -> event.cause()==Cause.DISCARD;
            default -> false;
        };
    }
    private void collectPrimaryTriggers(List<Event> events) {
        syncContinuous();
        for(int actor:new int[]{turnPlayer,1-turnPlayer})for(Piece p:List.copyOf(pieces.values())) {
            Catalog.Effect e=effectiveEffect(p);if(p.controller!=actor||e==null||e.spec()==null||hasFlag(p,"CANNOT_ACTIVATE"))continue;
            var spec=e.spec();String scope=actor+":"+p.card.id();
            if(e.oncePerTurn()&&used.contains(scope)||spec.oncePerDuel()&&duelUsed.contains(p.token))continue;
            if(spec.triggers().contains("ON_ACTIVATE")||spec.triggers().contains("CONTINUOUS"))continue;
            Event event=events.stream().filter(v->spec.triggers().stream().anyMatch(t->triggerMatches(t,v,p))).findFirst().orElse(null);
            if(event==null)continue;
            if(!Set.of(Zone.FIELD,Zone.SUPPORT,Zone.STADIUM,Zone.DISCARD,Zone.BANISHED).contains(p.zone))continue;
            if(spec.optional()){optionalTriggers.put(p.token,event);continue;}
            String selected=e.target().equals("chain")?Integer.toString(chain.size()):event.source();
            if(spec.targets()!=null){var candidates=select(spec.targets().selector(),p,selected,actor,spec.targets().filter());if(candidates.size()<spec.targets().min())continue;if(!candidates.isEmpty()&&!e.target().equals("chain"))selected=candidates.getFirst().token;}
            try {
                conditionEvent=event;
                if(faceDown.contains(p.token)){var profile=SpellTrapRules.profile(p.card);if(profile!=null&&turn-setTurn.getOrDefault(p.token,turn)<profile.minSetTurns())continue;}
                List<Payment> plan=validateSpec(p,e,selected,actor);paySpec(p,e,plan,actor);
                if(faceDown.remove(p.token))cue("TRAP_REVEAL",p,selected,actor,chain.size()+1);
            }catch(IllegalArgumentException ignored){continue;}finally{conditionEvent=null;}
            if(e.oncePerTurn())used.add(scope);Link link=new Link(p,selected,actor,chain.size()+1,e);Piece targetPiece=pieces.get(selected);if(targetPiece!=null)link.targetGeneration=targetPiece.generation;
            link.triggerEvent=event;captureEvent(link);chain.add(link);cue("CHAIN_LINK",p,selected,actor,link.number);priority=1-actor;open=false;passes=0;advance=false;
        }
    }

    private void collectChainReactions() {
        List<Event> events=pendingEvents.stream().filter(e->Set.of("ON_CHAIN","ON_CHAINED").contains(e.kind())).toList();
        pendingEvents.removeAll(events);if(!events.isEmpty())collectCompositeTriggers(events);
    }

    private Catalog.Effect activationOverride;
    private void activateStage(int actor,String token,String input){
        Piece p=owned(actor,token);Catalog.Effect original=effectiveEffect(p);require(original!=null&&original.spec()!=null,"cardworlds.error.target");
        String[] parts=input.split("\\|",2);String stageId=parts[0],selected=parts.length>1?parts[1]:"";
        var stage=vn.svarcade.tcg.data.EffectSpec.list(original.spec().stages()).stream().filter(s->s.id().equals(stageId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown effect stage"));
        require(stage.sourceZones().contains(p.zone.name())&&stage.effect().spec().triggers().contains("ON_ACTIVATE"),"cardworlds.error.automatic_trigger");
        String key=limitKey(p,stage.effect(),stage.id());require(!stage.effect().oncePerTurn()||!stageUsed.contains(turn+":"+key),"cardworlds.error.once_per_turn");
        activationOverride=stage.effect();validatingStage=stage.id();
        // The existing activation path validates ownership, priority, phase, target and every cost.
        try{activate(actor,token,selected);var link=chain.getLast();link.stageId=stage.id();if(stage.effect().oncePerTurn())stageUsed.add(turn+":"+key);}
        finally{activationOverride=null;validatingStage="primary";}
    }
    private EffectContext resolvingContext;
    private String validatingStage="primary";
    private record LiveStage(Piece source,vn.svarcade.tcg.data.EffectSpec.Stage stage) {}
    private final Map<String,List<LiveStage>> stageIndex=new HashMap<>();
    private final Set<String> stageUsed=new HashSet<>(),stageDuelUsed=new HashSet<>();
    private record Delayed(Link link,String trigger,int expires) {}
    private final List<Delayed> delayed=new ArrayList<>();
    private void captureEvent(Link link){if(link.triggerEvent!=null){link.context.eventCard=link.triggerEvent.card();link.context.eventTarget=link.triggerEvent.source();}}
    private String limitKey(Piece p,Catalog.Effect e,String stage) {
        String scope=e.spec()==null?"CARD_NAME":vn.svarcade.tcg.data.EffectSpec.value(e.spec().limitScope(),"EFFECT");
        return p.controller+":"+(scope.equals("CARD_NAME")?p.card.id():p.token)+(scope.equals("CARD")?"":":"+stage);
    }
    private void indexStages(){
        stageIndex.clear();for(Piece p:pieces.values()) {
            Catalog.Effect e=effectiveEffect(p);if(e==null||e.spec()==null)continue;
            for(var stage:vn.svarcade.tcg.data.EffectSpec.list(e.spec().stages())) {
                if(!stage.sourceZones().contains(p.zone.name())||faceDown.contains(p.token)||p.position==BattlePosition.FACE_DOWN_DEFENSE)continue;
                for(String trigger:stage.effect().spec().triggers())stageIndex.computeIfAbsent(trigger,k->new ArrayList<>()).add(new LiveStage(p,stage));
            }
        }
    }
    private Link stageLink(LiveStage live,Event event,boolean immediate) {
        Piece p=live.source();var stage=live.stage();Catalog.Effect e=stage.effect();String key=limitKey(p,e,stage.id());
        if(hasFlag(p,"CANNOT_ACTIVATE")||e.oncePerTurn()&&stageUsed.contains(turn+":"+key)||e.spec().oncePerDuel()&&stageDuelUsed.contains(key))return null;
        if(!immediate&&!chain.isEmpty()&&(e.speed()<2||e.speed()<chain.getLast().effect.speed()))return null;
        conditionEvent=event;validatingStage=stage.id();
        try {
            String selected=e.target().equals("chain")?Integer.toString(chain.size()):event.source();
            var spec=e.spec();if(spec.targets()!=null){var candidates=select(spec.targets().selector(),p,selected,p.controller,spec.targets().filter());if(candidates.size()<spec.targets().min())return null;if(!candidates.isEmpty()&&!e.target().equals("chain"))selected=candidates.getFirst().token;}
            var plan=validateSpec(p,e,selected,p.controller);if(life[p.controller]<=e.lifeCost())return null;
            Link link=new Link(p,selected,p.controller,chain.size()+1,e);link.stageId=stage.id();link.triggerEvent=event;captureEvent(link);
            paySpec(p,e,plan,p.controller);life[p.controller]-=e.lifeCost();for(var payment:plan)for(Piece cost:payment.cards())link.context.paidCosts.add(new EffectContext.Selection(cost.token,cost.generation));
            if(e.oncePerTurn())stageUsed.add(turn+":"+key);if(spec.oncePerDuel())stageDuelUsed.add(key);
            Piece targetPiece=pieces.get(selected);if(targetPiece!=null)link.targetGeneration=targetPiece.generation;return link;
        }catch(IllegalArgumentException ignored){return null;}finally{conditionEvent=null;validatingStage="primary";}
    }
    private void collectCompositeTriggers(List<Event> events){
        collectPrimaryTriggers(events);indexStages();
        for(Event event:events){
            List<LiveStage> relevant=new ArrayList<>(stageIndex.getOrDefault(event.kind(),List.of()));
            if(event.kind().equals("move"))for(var entry:stageIndex.entrySet())if(!entry.getKey().equals("WOULD_DESTROY"))
                for(var live:entry.getValue())if(triggerMatches(entry.getKey(),event,live.source()))relevant.add(live);
            for(var live:new LinkedHashSet<>(relevant)){
                if(!live.stage().listenAny()&&!event.card().isBlank()&&!event.card().equals(live.source().token))continue;
                Link link=stageLink(live,event,false);if(link==null)continue;
                chain.add(link);cue("CHAIN_LINK",link.source,link.target,link.actor,link.number);priority=1-link.actor;open=false;passes=0;advance=false;
            }
            for(var it=delayed.iterator();it.hasNext();){var pending=it.next();
                if(pending.expires()<turn){it.remove();continue;}
                if(!pending.trigger().equals(event.kind()))continue;
                Link saved=pending.link();Link l=new Link(saved.source,saved.target,saved.actor,chain.size()+1,saved.effect);l.targetGeneration=saved.targetGeneration;l.context.selectedCards.putAll(saved.context.selectedCards);l.context.flags.putAll(saved.context.flags);l.context.paidCosts.addAll(saved.context.paidCosts);l.triggerEvent=event;captureEvent(l);chain.add(l);it.remove();cue("CHAIN_LINK",l.source,l.target,l.actor,l.number);priority=1-l.actor;open=false;passes=0;advance=false;
            }
        }
        stageUsed.removeIf(k->!k.startsWith(turn+":"));
    }
    private void schedule(Link parent,vn.svarcade.tcg.data.EffectSpec.Operation op){
        if(delayed.size()>=64)return;
        var original=parent.effect.spec();var spec=new vn.svarcade.tcg.data.EffectSpec(List.of("ON_ACTIVATE"),List.of(),List.of(),original.targets(),op.children(),false,false,null,original.vfx());
        var effect=new Catalog.Effect("composite",0,2,0,"none",List.of("DRAW","STANDBY","MAIN1","BATTLE","MAIN2","END"),false,spec);
        Link next=new Link(parent.source,parent.target,parent.actor,parent.number,effect);next.targetGeneration=parent.targetGeneration;next.context.selectedCards.putAll(parent.context.selectedCards);next.context.flags.putAll(parent.context.flags);
        delayed.add(new Delayed(next,flag(op,"trigger","ON_TURN_END"),expiryTurn(op.duration())));cue("CAST_STATUS",parent.source,parent.target,parent.actor,parent.number);
    }
    private boolean protectFromDestruction(Piece victim,Piece attackerPiece,Cause cause){
        indexStages();Event event=new Event(history.size()+1,"WOULD_DESTROY",victim.token,victim.owner,victim.controller,victim.zone,Zone.DISCARD,cause,attackerPiece==null?"":attackerPiece.token,chain.size());
        EffectContext previous=resolvingContext;Event previousEvent=conditionEvent;
        for(var live:List.copyOf(stageIndex.getOrDefault("WOULD_DESTROY",List.of()))){
            if(!live.stage().listenAny()&&live.source()!=victim)continue;Link link=stageLink(live,event,true);if(link==null)continue;
            conditionEvent=event;resolvingContext=link.context;
            try{runOperations(link,link.effect.spec().operations(),0);if(link.context.flags.containsKey("prevent_destroy")){conditionEvent=previousEvent;resolvingContext=previous;return true;}}
            finally{conditionEvent=previousEvent;resolvingContext=previous;}
        }return false;
    }
    /** QA fixture uses real summon, cost, Chain and effect paths. No client assigns counters. */
    public synchronized void qaSpecialScenario(int actor,String id,boolean threshold){
        require(Boolean.getBoolean("cardworlds.qa"),"Special QA requires QA mode");
        var card=catalog.card(id);require(id.startsWith("special_")&&card.effect()!=null,"Unavailable special card");
        qaReset(actor,5);for(int seat=0;seat<2;seat++)for(int i=0;i<30;i++)qaAdd(seat,"charmander",Zone.DECK);
        Piece a=qaAdd(actor,"charmander",Zone.FIELD),b=qaAdd(actor,"squirtle",Zone.FIELD),source=qaAdd(actor,id,Zone.HAND);
        Piece trap=qaAdd(1-actor,"attack_mirror",Zone.SUPPORT);faceDown.add(trap.token);setTurn.put(trap.token,turn-1);
        Piece ally=qaAdd(actor,"pikachu",Zone.FIELD),enemy=qaAdd(1-actor,"squirtle",Zone.FIELD);
        act(actor,new Action("play",source.token,a.token+","+b.token),revision);qaSettle();
        if(threshold){int until=turn+4,guard=160;
            while(turn<until&&guard-->0){qaSettle();act(turnPlayer,new Action("next","",""),revision);qaSettle();}
            require(turn>=until,"Counter generation did not advance");
            int mainGuard=80;while((turnPlayer==actor||phase!=Phase.MAIN1)&&mainGuard-->0){qaSettle();act(turnPlayer,new Action("next","",""),revision);qaSettle();}
            require(turnPlayer!=actor&&phase==Phase.MAIN1,"Opponent Main Phase was not reached");
            Piece spell=qaAdd(1-actor,"flamethrower",Zone.HAND);act(1-actor,new Action("activate",spell.token,""),revision);qaSettle();
            require(cues.stream().anyMatch(c->c.semantic().equals("CHAIN_NEGATE")),"Advanced Chain did not negate");
        }else{
            String target=card.effect().target().equals("ally")?ally.token:card.effect().target().equals("enemy")?enemy.token:"";
            act(actor,new Action("activate",source.token,target),revision);qaSettle();
        }
        revision++;
    }
    private void qaSettle(){int guard=80;while((!chain.isEmpty()||!open)&&guard-->0)act(priority,new Action("pass","",""),revision);require(open&&chain.isEmpty(),"Special QA did not settle");}

}
