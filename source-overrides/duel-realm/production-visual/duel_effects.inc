
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
        if(s.equals("SELF")||s.equals("SOURCE")){if(source!=null)list.add(source);}
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
        require(!spec.oncePerDuel()||!duelUsed.contains(p.token),"cardworlds.error.once_per_duel");
        for(var c:vn.svarcade.tcg.data.EffectSpec.list(spec.conditions()))require(condition(c,p,selected,actor),"cardworlds.error.condition");
        if(spec.targets()!=null&&spec.targets().min()>0) {
            var targets=select(spec.targets().selector(),p,selected,actor,spec.targets().filter());
            require(targets.size()>=spec.targets().min(),"cardworlds.error.target");
            for(Piece targetPiece:targets)require(!hasFlag(targetPiece,"PREVENT_TARGET")||targetPiece.controller==actor,"cardworlds.error.protected_target");
        }
        List<Payment> plan=new ArrayList<>();Set<String> reserved=new HashSet<>();int lp=e.lifeCost();
        for(var c:vn.svarcade.tcg.data.EffectSpec.list(spec.costs())) {
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
        if(e.spec().oncePerDuel())duelUsed.add(source.token);
        for(Payment payment:plan) {
            var c=payment.cost();
            if(c.type().equals("LP_COST")){life[actor]-=c.amount();continue;}
            for(Piece p:payment.cards())switch(c.type()) {
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
                case "SHUFFLE" -> {var deck=new ArrayList<>(select("DECK",source,"",recipient,null));Collections.shuffle(deck,effectRandom);reorderDeck(recipient,deck);}
                case "MILL" -> {for(Piece p:select("TOP_DECK",source,"",recipient,null).stream().limit(Math.max(0,op.amount())).toList()){move(p,Zone.DISCARD,Cause.DRAW,source.token,l.number);}}
                case "LOOK_TOP_DECK" -> {select("TOP_DECK",source,"",l.actor,null).stream().limit(Math.max(0,op.amount())).forEach(p->p.lookedBy=l.actor);cue("SEARCH",source,"",l.actor,l.number);}
                case "REORDER_DECK" -> {var deck=new ArrayList<>(select("TOP_DECK",source,"",l.actor,null).stream().limit(Math.max(1,op.amount())).toList());switch(flag(op,"order","REVERSE")){case "POWER_ASC" -> deck.sort(Comparator.comparingInt(p->p.card.power()));case "POWER_DESC" -> deck.sort(Comparator.comparingInt((Piece p)->p.card.power()).reversed());default -> Collections.reverse(deck);}reorderDeck(l.actor,deck);}
                case "NEGATE_EFFECT","NEGATE_ACTIVATION" -> {int n=Integer.parseInt(l.target)-1;if(n>=0&&n<l.number-1){Link victim=chain.get(n);if(op.type().equals("NEGATE_EFFECT"))victim.negated=true;else victim.activationNegated=true;cue("CHAIN_NEGATE",source,victim.source.token,l.actor,victim.number);signal("ON_NEGATE",source,victim.source);}}
                case "NEGATE_ATTACK" -> {if(attacker!=null){cue("CHAIN_NEGATE",source,attacker,l.actor,l.number);attacker=null;target=null;}}
                case "CANNOT_SUMMON" -> {int player=op.target()!=null&&op.target().equals("ALL_ENEMIES")?1-l.actor:chosen.isEmpty()?l.actor:chosen.getFirst().controller;summonLocks[player]=expiryTurn(op.duration());cue("DEBUFF",source,"",player,l.number);}
                case "REDIRECT_ATTACK" -> {if(!chosen.isEmpty()&&attacker!=null&&chosen.getFirst().controller!=pieces.get(attacker).controller)target=chosen.getFirst().token;}
                case "REDIRECT_TARGET" -> {int n=chain.size()-2;if(n>=0&&!chosen.isEmpty()){chain.get(n).target=chosen.getFirst().token;chain.get(n).targetGeneration=chosen.getFirst().generation;}}
                default -> {
                    for(Piece p:chosen) {
                        switch(op.type()) {
                            case "MODIFY_POWER" -> {p.boost+=op.amount();if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"BOOST",op.amount(),expiryTurn(op.duration()),p.controller,null));else p.permanentBoost+=op.amount();cue(op.amount()>=0?"BUFF":"DEBUFF",source,p.token,l.actor,l.number);}
                            case "SET_POWER" -> {int old=p.fixedPower;p.fixedPower=Math.max(0,op.amount());if(op.duration()!=null&&!op.duration().equals("PERMANENT"))expiries.add(new Expiry(p.token,"SET_POWER",old,expiryTurn(op.duration()),p.controller,null));cue("BUFF",source,p.token,l.actor,l.number);}
                            case "SWAP_POWER" -> {int a=power(source),b=power(p);if(op.duration()!=null&&!op.duration().equals("PERMANENT")){expiries.add(new Expiry(source.token,"SET_POWER",source.fixedPower,expiryTurn(op.duration()),source.controller,null));expiries.add(new Expiry(p.token,"SET_POWER",p.fixedPower,expiryTurn(op.duration()),p.controller,null));}source.fixedPower=b;p.fixedPower=a;cue("BUFF",source,p.token,l.actor,l.number);}
                            case "DESTROY" -> {if(hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,l.number);else if(p.shield>0){p.shield--;cue("SHIELD",p,source.token,p.controller,l.number);}else{move(p,Zone.DISCARD,Cause.DESTROY,source.token,l.number);signal("ON_DESTROY",source,p);}}
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
                            case "ADD_COUNTER" -> p.counters.merge(flag(op,"counter","charge"),Math.max(0,op.amount()),Integer::sum);
                            case "REMOVE_COUNTER" -> p.counters.compute(flag(op,"counter","charge"),(k,n)->Math.max(0,(n==null?0:n)-Math.max(0,op.amount())));
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
    private void collectCompositeTriggers(List<Event> events) {
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
            link.triggerEvent=event;chain.add(link);cue("CHAIN_LINK",p,selected,actor,link.number);priority=1-actor;open=false;passes=0;advance=false;
        }
    }

    private void collectChainReactions() {
        List<Event> events=pendingEvents.stream().filter(e->Set.of("ON_CHAIN","ON_CHAINED").contains(e.kind())).toList();
        pendingEvents.removeAll(events);if(!events.isEmpty())collectCompositeTriggers(events);
    }
