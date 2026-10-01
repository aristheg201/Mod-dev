from pathlib import Path
here=Path(__file__).resolve().parent
p=Path('src/main/java/vn/svarcade/tcg/duel/Duel.java');s=p.read_text()
if 'private final Map<String,List<LiveStage>> stageIndex' in s: raise RuntimeError('Advanced engine applied twice; restore sources first')
s=s.replace('Event triggerEvent; boolean negated, activationNegated;', 'final EffectContext context; String stageId="primary"; Event triggerEvent; boolean negated, activationNegated;')
# Constructors assign context only once after delegated constructor resolution.
s=s.replace('this.effect=effect;', 'this.effect=effect;this.context=new EffectContext(p.token,actor,number);')
# Actual constructor uses e rather than effect in this snapshot.
s=s.replace('this.effect=e;', 'this.effect=e;this.context=new EffectContext(p.token,actor,number);')
s=s.replace('case "attack" -> attack(actor,action.card,action.target);', 'case "activate_stage" -> activateStage(actor,action.card,action.target);\n            case "attack" -> attack(actor,action.card,action.target);')
s=s.replace('Catalog.Effect e=effectiveEffect(p);require(e!=null,','Catalog.Effect e=activationOverride==null?effectiveEffect(p):activationOverride;require(e!=null,')
s=s.replace('link.triggerEvent=conditionEvent;', 'link.triggerEvent=conditionEvent;captureEvent(link);for(var payment:paymentPlan)for(Piece paid:payment.cards())link.context.paidCosts.add(new EffectContext.Selection(paid.token,paid.generation));')
s=s.replace('link.triggerEvent=event;', 'link.triggerEvent=event;captureEvent(link);')
s=s.replace('private void collectCompositeTriggers(List<Event> events)', 'private void collectPrimaryTriggers(List<Event> events)')
s=s.replace('if(e.spec()!=null){\n            conditionEvent=l.triggerEvent;', 'if(e.spec()!=null&&e.operation().equals("composite")){\n            conditionEvent=l.triggerEvent;resolvingContext=l.context;')
s=s.replace('finally{conditionEvent=null;}\n        }\n        switch(e.operation())', 'finally{conditionEvent=null;resolvingContext=null;}\n        }\n        switch(e.operation())')
s=s.replace('if(t==null||t.generation!=l.targetGeneration){note(l.source.card.name()+" lost its target.");return;}', 'if(t==null||t.generation!=l.targetGeneration){note(l.source.card.name()+" lost its target.");if(e.spec()==null||!e.operation().equals("composite"))return;l.target="";t=null;}')
s=s.replace('try {validateTarget(l.actor,e,l.target);}catch(IllegalArgumentException ex){note(l.source.card.name()+" lost its target.");return;}', 'try{validateTarget(l.actor,e,l.target);}catch(IllegalArgumentException ex){note(l.source.card.name()+" lost its target.");if(e.spec()==null||!e.operation().equals("composite"))return;l.target="";t=null;}')
s=s.replace('move(p,destination,Cause.SET,p.token,0);faceDown.add(p.token);', 'move(p,destination,Cause.SET,p.token,0);signal("ON_SET",p,null);faceDown.add(p.token);')
s=s.replace('if(hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,0);', 'if(protectFromDestruction(p,source,Cause.BATTLE)||hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,0);')
s=s.replace('if(hasFlag(t,"PREVENT_DESTROY"))cue("SHIELD",t,l.source.token,t.controller,l.number);', 'if(protectFromDestruction(t,l.source,Cause.DESTROY)||hasFlag(t,"PREVENT_DESTROY"))cue("SHIELD",t,l.source.token,t.controller,l.number);')
s=s.replace('if(hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,l.number);', 'if(protectFromDestruction(p,source,Cause.DESTROY)||hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,l.number);')
s=s.replace('var spec=e.spec();if(spec==null)return List.of();','var spec=e.spec();if(spec==null)return List.of();')
s=s.replace('!duelUsed.contains(p.token)', '!duelUsed.contains(limitKey(p,e,activeStage)))') if False else s
s=s.replace('!duelUsed.contains(p.token)', '!duelUsed.contains(limitKey(p,e,validatingStage))')
s=s.replace('duelUsed.add(source.token);','duelUsed.add(limitKey(source,e,validatingStage));')
s=s.replace('!used.contains(actor+":"+p.card.id())', '!used.contains(activationOverride==null?actor+":"+p.card.id():turn+":"+limitKey(p,e,validatingStage))')
s=s.replace('used.add(actor+":"+p.card.id());', 'used.add(activationOverride==null?actor+":"+p.card.id():turn+":"+limitKey(p,e,validatingStage));')
s=s.replace('l.effect.spec().conditions()', 'l.effect.spec().resolutionConditions()')
s=s.replace('e.spec().conditions()))if(!condition(c,l.source', 'e.spec().resolutionConditions()))if(!condition(c,l.source')
# Counter reservations are separate from card reservations.
s=s.replace('Set<String> reserved=new HashSet<>();int lp=e.lifeCost();','Set<String> reserved=new HashSet<>();Map<String,Integer> counterReservations=new HashMap<>();int lp=e.lifeCost();')
s=s.replace('if(c.type().equals("LP_COST")){lp+=c.amount();', '''if(c.type().equals("COUNTER_COST")) {int needed=counterReservations.merge(c.counter(),c.amount(),Integer::sum);
                require(p.counters.getOrDefault(c.counter(),0)>=needed,"cardworlds.error.cost");plan.add(new Payment(c,List.of(p)));continue;}
            if(c.type().equals("LP_COST")){lp+=c.amount();''')
s=s.replace('case "DISCARD" -> move(p,Zone.DISCARD,Cause.DISCARD,source.token,0);','case "COUNTER_COST" -> {p.counters.compute(c.counter(),(k,n)->n-c.amount());cue("CHARGE",source,p.token,actor,0);}\n                case "DISCARD" -> move(p,Zone.DISCARD,Cause.DISCARD,source.token,0);')
s=s.replace('private List<Piece> select(String selector,Piece source,String selected,int actor,vn.svarcade.tcg.data.EffectSpec.Filter filter) {','''private List<Piece> select(String selector,Piece source,String selected,int actor,vn.svarcade.tcg.data.EffectSpec.Filter filter) {''')
s=s.replace('if(s.equals("SELF")||s.equals("SOURCE"))', '''if(s.equals("EVENT_CARD")||s.equals("EVENT_TARGET")) {String token=conditionEvent==null?"":s.equals("EVENT_CARD")?conditionEvent.card():conditionEvent.source();Piece p=pieces.get(token);if(p!=null)list.add(p);}
        else if(Set.of("PAID_COSTS","DESTROYED_THIS_RESOLUTION","BANISHED_THIS_RESOLUTION","REMEMBERED").contains(s)) {
            if(resolvingContext!=null){var remembered=switch(s){case "PAID_COSTS"->resolvingContext.paidCosts;case "DESTROYED_THIS_RESOLUTION"->resolvingContext.destroyedThisResolution;case "BANISHED_THIS_RESOLUTION"->resolvingContext.banishedThisResolution;default->resolvingContext.selectedCards.getOrDefault(resolvingContext.flags.getOrDefault("memory","selected"),List.of());};
                for(var memory:remembered){Piece p=pieces.get(memory.token());if(p!=null&&p.generation==memory.generation())list.add(p);}}
        }
        else if(s.equals("PREVIOUS_CHAIN_SOURCE")||s.equals("PREVIOUS_CHAIN_TARGET")){int i=resolvingContext==null?chain.size()-1:resolvingContext.chainLink-2;if(i>=0&&i<chain.size()){var previous=chain.get(i);Piece p=s.endsWith("SOURCE")?previous.source:pieces.get(previous.target);if(p!=null)list.add(p);}}
        else if(s.equals("SELF")||s.equals("SOURCE"))''')
s=s.replace('case "COUNTER_AT_LEAST" ->', '''case "COUNTER_AT_MOST" -> targets.stream().anyMatch(p->p.counters.getOrDefault(value,0)<=c.amount());
            case "SEEN_BY_CONTROLLER" -> targets.stream().anyMatch(p->p.inspectedBy.contains(actor));
            case "EVENT_TYPE" -> conditionEvent!=null&&pieces.containsKey(conditionEvent.card())&&pieces.get(conditionEvent.card()).card.category().equals(value);
            case "NOT_SELF" -> targets.stream().anyMatch(p->p!=source);\n            case "SURVIVED" -> targets.stream().anyMatch(p->p.zone==Zone.FIELD||p.zone==Zone.SUPPORT||p.zone==Zone.STADIUM);
            case "COUNTER_AT_LEAST" ->''')
s=s.replace('final Set<String> flags=new HashSet<>();', 'final Set<Integer> inspectedBy=new HashSet<>();final Set<String> flags=new HashSet<>();')
s=s.replace('if(op.type().equals("IF")){', '''if(op.type().equals("DELAY")){schedule(l,op);continue;}
            if(op.flags()!=null&&op.flags().containsKey("memory"))l.context.flags.put("memory",op.flags().get("memory"));
            if(op.type().equals("IF")){''')
s=s.replace('case "SHUFFLE" ->', '''case "REMEMBER" -> l.context.remember(flag(op,"memory","selected"),chosen.stream().map(p->new EffectContext.Selection(p.token,p.generation)).toList());
                case "REPLACE_DESTROY" -> {l.context.flags.put("prevent_destroy","true");cue("SHIELD",source,l.target,l.actor,l.number);}
                case "INSPECT_SET" -> {for(Piece p:chosen)if(faceDown.contains(p.token)&&p.controller!=l.actor){p.inspectedBy.add(l.actor);cue("SEARCH",source,p.token,l.actor,l.number);}}
                case "SHUFFLE" ->''')
s=s.replace('case "ADD_COUNTER" -> p.counters.merge(flag(op,"counter","charge"),Math.max(0,op.amount()),Integer::sum);', 'case "ADD_COUNTER" -> {String counter=flag(op,"counter","charge");p.counters.compute(counter,(k,n)->Math.min(Integer.parseInt(flag(op,"max","99")),(n==null?0:n)+Math.max(0,op.amount())));cue("CHARGE",source,p.token,l.actor,l.number);}')
s=s.replace('case "REMOVE_COUNTER" -> p.counters.compute(flag(op,"counter","charge"),(k,n)->Math.max(0,(n==null?0:n)-Math.max(0,op.amount())));', 'case "REMOVE_COUNTER" -> {p.counters.compute(flag(op,"counter","charge"),(k,n)->Math.max(0,(n==null?0:n)-Math.max(0,op.amount())));cue("CHARGE",source,p.token,l.actor,l.number);}')
s=s.replace('int n=chain.size()-2;', 'int n=l.number-2;')
s=s.replace('case "NEGATE_EFFECT","NEGATE_ACTIVATION" -> {int n=Integer.parseInt(l.target)-1;', 'case "NEGATE_EFFECT","NEGATE_ACTIVATION" -> {int n=l.target.matches("[0-9]+")?Integer.parseInt(l.target)-1:l.number-2;')
s=s.replace('p.generation++;Event event=', '''p.generation++;p.inspectedBy.clear();if(resolvingContext!=null){var memory=new EffectContext.Selection(p.token,p.generation);if(to==Zone.BANISHED)resolvingContext.banishedThisResolution.add(memory);if(cause==Cause.BATTLE||cause==Cause.DESTROY)resolvingContext.destroyedThisResolution.add(memory);}
        Event event=''')
s=s.replace('boolean hiddenTarget=target!=null&&(target.zone==Zone.DECK||target.zone==Zone.HAND||target.zone==Zone.EXTRA);','boolean hiddenTarget=target!=null&&(target.zone==Zone.DECK||target.zone==Zone.HAND||target.zone==Zone.EXTRA);')
s=s.replace('p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p));','p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p),Map.copyOf(p.counters));')
s=s.replace('String position,Catalog.Effect effect) {public VisibleCard', 'String position,Catalog.Effect effect,Map<String,Integer> counters) {public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position,Catalog.Effect effect){this(token,name,species,aspects,type,category,power,text,zone,controller,position,effect,Map.of());}public VisibleCard')
s=s.replace('position,null);}}','position,null,Map.of());}}')
# Players who privately inspected a set card receive its identity, spectators still see the back.
s=s.replace('(supportSet||monsterSet)&&p.controller!=viewer', '(supportSet||monsterSet)&&p.controller!=viewer&&!p.inspectedBy.contains(viewer)')
s=s.replace('optionalTriggers.clear();continuous.clear();', 'stageIndex.clear();delayed.clear();stageUsed.clear();stageDuelUsed.clear();optionalTriggers.clear();continuous.clear();')
s=s.rstrip()[:-1]+(here/'advanced_effects.inc').read_text()+'\n}\n';p.write_text(s)
