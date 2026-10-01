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
