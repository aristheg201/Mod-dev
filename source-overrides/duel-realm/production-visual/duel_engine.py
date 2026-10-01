from pathlib import Path
here=Path(__file__).resolve().parent
p=Path('src/main/java/vn/svarcade/tcg/duel/Duel.java')
s=p.read_text()
if 'effectPrimitiveCount()' not in s:
    def edit(old,new,n=1):
        global s
        assert s.count(old)==n,(old[:90],s.count(old),n)
        s=s.replace(old,new)
    edit('private void qaReset(int actor,int qaTurn)', 'void qaReset(int actor,int qaTurn)')
    edit('private Piece qaAdd(int actor,String cardId,Zone zone)', 'Piece qaAdd(int actor,String cardId,Zone zone)')
    edit('String position) {}', 'String position,Catalog.Effect effect) {public VisibleCard(String token,String name,String species,List<String> aspects,String type,String category,int power,String text,Zone zone,int controller,String position){this(token,name,species,aspects,type,category,power,text,zone,controller,position,null);}}')
    edit('p.card.text(),p.zone,p.controller,p.position.name());', 'p.card.text(),p.zone,p.controller,p.position.name(),effectiveEffect(p));')
    edit('List<String> chain, String winner, List<String> log) {}','List<String> chain, String winner, List<String> log, List<Cue> cues) {}')
    edit('BattlePosition position=BattlePosition.ATTACK;', '''int fixedPower=-1,extraAttacks,lookedBy=-1,permanentBoost;
        Catalog.Effect grantedEffect;
        final Set<String> flags=new HashSet<>();final Map<String,Integer> counters=new HashMap<>();
        BattlePosition position=BattlePosition.ATTACK;''')
    edit('final Piece source; final String target;', 'final Piece source; String target;')
    edit('boolean negated, activationNegated;', 'Event triggerEvent; boolean negated, activationNegated;')
    edit('pieces.clear();chain.clear();history.clear();', 'optionalTriggers.clear();continuous.clear();activatedContinuous.clear();expiries.clear();revealed.clear();duelUsed.clear();cues.clear();summonLocks[0]=-1;summonLocks[1]=-1;pieces.clear();chain.clear();history.clear();')
    edit('mainAction(actor);Piece p=owned(actor,token);require(p.zone==Zone.HAND&&p.card.category().equals("pokemon"),', 'mainAction(actor);Piece p=owned(actor,token);require(summonLocks[actor]<turn&&!hasFlag(p,"CANNOT_SUMMON"),"cardworlds.error.cannot_summon");require(p.zone==Zone.HAND&&p.card.category().equals("pokemon"),')
    edit('if(winner<0&&chain.isEmpty())collectTriggers();', 'if(winner<0){if(chain.isEmpty())collectTriggers();else collectChainReactions();}')
    edit('mainAction(actor);Piece p=owned(actor,token);require(p.zone==Zone.HAND||p.zone==Zone.EXTRA,', 'mainAction(actor);Piece p=owned(actor,token);require(summonLocks[actor]<turn&&!hasFlag(p,"CANNOT_SUMMON"),"cardworlds.error.cannot_summon");require(p.zone==Zone.HAND||p.zone==Zone.EXTRA,')
    edit('p.position=next;p.positionTurn=turn;', 'BattlePosition previous=p.position;p.position=next;p.positionTurn=turn;cue("POSITION_CHANGE",p,"",actor,0);signal("ON_POSITION_CHANGE",p,null);if(previous==BattlePosition.FACE_DOWN_DEFENSE)signal("ON_FLIP",p,null);')
    edit('case \"activate\" -> activate(actor,action.card,action.target);', 'case \"activate\" -> {conditionEvent=optionalTriggers.get(action.card);try{activate(actor,action.card,action.target);optionalTriggers.remove(action.card);}finally{conditionEvent=null;}}')
    edit('Catalog.Effect e=p.card.effect();require(e!=null,','Catalog.Effect e=effectiveEffect(p);require(e!=null,')
    edit('require(!summonLocked(p),"This summon stage is locked.");\n        require(p.zone==Zone.HAND', 'require(!summonLocked(p),"This summon stage is locked.");require(e.spec()==null||e.spec().triggers().contains("ON_ACTIVATE")||e.spec().triggers().contains("CONTINUOUS")||e.spec().optional()&&optionalTriggers.containsKey(p.token),"cardworlds.error.automatic_trigger");\n        require(p.zone==Zone.HAND')

    edit('        boolean fromHand=p.zone==Zone.HAND;', '        List<Payment> paymentPlan=validateSpec(p,e,chosen,actor);\n        boolean fromHand=p.zone==Zone.HAND;')
    edit('        if(wasSet)faceDown.remove(p.token);','        paySpec(p,e,paymentPlan,actor);\n        if(wasSet)faceDown.remove(p.token);')
    edit('        if(fromHand) {','        if(fromHand&&p.zone==Zone.HAND) {')
    edit('Piece p=pieces.get(selected);require(p!=null,"Choose a target.");','Piece p=pieces.get(selected);require(p!=null,"Choose a target.");require(p.controller==actor||!hasFlag(p,"PREVENT_TARGET"),"cardworlds.error.protected_target");')
    edit('case "destroy" -> {if(t.shield>0)t.shield--;else move(t,Zone.DISCARD,Cause.DESTROY,l.source.token,l.number);}', 'case "destroy" -> {if(hasFlag(t,"PREVENT_DESTROY"))cue("SHIELD",t,l.source.token,t.controller,l.number);else if(t.shield>0)t.shield--;else move(t,Zone.DISCARD,Cause.DESTROY,l.source.token,l.number);}')
    edit('pieces.values().forEach(p->{p.attacked=false;p.boost=0;});','pieces.values().forEach(p->{p.attacked=false;p.boost=p.permanentBoost+expiries.stream().filter(e->e.token().equals(p.token)&&e.key().equals("BOOST")&&e.endTurn()>=turn).mapToInt(Expiry::value).sum();});')
    edit('Link link=new Link(p,chosen,actor,chain.size()+1);', 'Link link=new Link(p,chosen,actor,chain.size()+1,e);')
    edit('chain.add(link);note("Chain "+chain.size()+": "+p.card.name());', '''link.triggerEvent=conditionEvent;chain.add(link);note("Chain "+chain.size()+": "+p.card.name());
        cue(p.card.category().equals("pokemon")?"CAST_STATUS":p.card.category().equals("reaction")?"TRAP_REVEAL":"SPELL_ACTIVATE",p,chosen,actor,link.number);
        cue("CHAIN_LINK",p,chosen,actor,link.number);signal("ON_CHAIN",p,targetPiece);signal("ON_CHAINED",p,targetPiece);''')
    edit('        if(e.target().equals("none")) return;', '''        if(e.spec()!=null&&e.spec().targets()!=null){
            var spec=e.spec();var candidates=select(spec.targets().selector(),null,selected,actor,spec.targets().filter());
            if(!spec.targets().selector().equals("SELF")&&!spec.targets().selector().equals("SOURCE"))
                require(candidates.size()>=spec.targets().min(),"cardworlds.error.target");
        }
        if(e.target().equals("none")) return;''')
    edit('require(p.zone==Zone.FIELD&&!p.attacked,"That Pokemon cannot attack again.");','require(p.zone==Zone.FIELD&&(!p.attacked||p.extraAttacks>0),"That Pokemon cannot attack again.");require(!hasFlag(p,"CANNOT_ATTACK"),"cardworlds.error.cannot_attack");')
    edit('        p.attacked=true;attacker=token;target=selected;', '''        if(p.attacked)p.extraAttacks--;p.attacked=true;attacker=token;target=selected;
        var meta=vn.svarcade.tcg.data.EffectContent.attackPresentation(p.card);
        String mode=meta==null?"MELEE":vn.svarcade.tcg.data.EffectSpec.value(meta.mode(),"MELEE");
        cue("CHARGE",p,selected,actor,0);
        if(selected==null||selected.isBlank())cue("DIRECT_ATTACK",p,"",actor,0);
        signal("ON_ATTACK_DECLARE",p,pieces.get(selected));if(pieces.get(selected)!=null)signal("ON_ATTACKED",pieces.get(selected),p);''')
    edit('            if(!l.negated&&!l.activationNegated) effect(l);', '''            cue("CHAIN_RESOLVE",l.source,l.target,l.actor,l.number);signal("ON_CHAIN_RESOLVE",l.source,pieces.get(l.target));
            if(!l.negated&&!l.activationNegated) effect(l);''')
    edit('            else note(l.source.card.name()+" was negated.");','            else {note(l.source.card.name()+" was negated.");cue("CHAIN_NEGATE",l.source,l.target,l.actor,l.number);signal("ON_NEGATE",l.source,null);}')
    edit('        switch(e.operation()) {', '''        if(e.spec()!=null){
            conditionEvent=l.triggerEvent;
            try {for(var c:vn.svarcade.tcg.data.EffectSpec.list(e.spec().conditions()))if(!condition(c,l.source,l.target,l.actor))return;
            if(!e.spec().triggers().contains("CONTINUOUS"))runOperations(l,e.spec().operations(),0);
            else {activatedContinuous.add(l.source.token);syncContinuous();}
            note(l.source.card.name()+" resolves.");return;}finally{conditionEvent=null;}
        }
        switch(e.operation()) {''')
    edit('case "heal" -> life[l.actor]=Math.addExact(life[l.actor],e.amount());', 'case "heal" -> {life[l.actor]=Math.addExact(life[l.actor],e.amount());cue("HEAL",l.source,l.target,l.actor,l.number);}')
    edit('case "shield" -> t.shield+=e.amount();','case "shield" -> {t.shield+=e.amount();cue("SHIELD",l.source,t.token,l.actor,l.number);}')
    edit('case "boost" -> t.boost+=e.amount();','case "boost" -> {t.boost+=e.amount();cue("BUFF",l.source,t.token,l.actor,l.number);}')
    edit('chain.get(n).activationNegated=true;}}','chain.get(n).activationNegated=true;cue("CHAIN_NEGATE",l.source,chain.get(n).source.token,l.actor,n+1);signal("ON_NEGATE",l.source,chain.get(n).source);}}')
    edit('if(count(1-turnPlayer,Zone.FIELD)==0)damage(1-turnPlayer,power(a));', 'if(count(1-turnPlayer,Zone.FIELD)==0){cue(\"ATTACK_SPECIAL\",a,\"\",a.controller,0);damage(1-turnPlayer,power(a));}')
    edit('d.position=BattlePosition.DEFENSE;note(', 'd.position=BattlePosition.DEFENSE;signal(\"ON_FLIP\",d,a);cue(\"POSITION_CHANGE\",d,a.token,d.controller,0);note(')
    edit('        int difference=attack-power(d);','        cue("IMPACT",a,d.token,a.controller,0);\n        int difference=attack-power(d);')
    edit('        int attack=power(a)+', '''        var action=vn.svarcade.tcg.data.EffectContent.attackPresentation(a.card);
        String mode=action==null?"MELEE":vn.svarcade.tcg.data.EffectSpec.value(action.mode(),"MELEE");
        cue(mode.equals("MELEE")?"ATTACK_PHYSICAL":mode.equals("STATUS")?"CAST_STATUS":"ATTACK_SPECIAL",a,d.token,a.controller,0);
        int attack=power(a)+''')
    edit('if(difference>0)destroyBattle(d,a);else if(difference<0)damage(a.controller,-difference);','if(difference>0){destroyBattle(d,a);if(hasFlag(a,"PIERCE"))damage(d.controller,difference);}else if(difference<0)damage(a.controller,-difference);')
    edit('result+=m.power();return result;}', 'result+=m.power();return compositePower(p,result);}')
    edit('        for(int seat:new int[]{turnPlayer,1-turnPlayer})for(Piece source:pieces.values()){','        collectCompositeTriggers(events);\n        for(int seat:new int[]{turnPlayer,1-turnPlayer})for(Piece source:pieces.values()){')
    edit('private void destroyBattle(Piece p,Piece source) {if(p.shield>0)p.shield--;else move(p,Zone.DISCARD,Cause.BATTLE,source.token,0);}', 'private void destroyBattle(Piece p,Piece source) {if(hasFlag(p,"PREVENT_DESTROY"))cue("SHIELD",p,source.token,p.controller,0);else if(p.shield>0)p.shield--;else{move(p,Zone.DISCARD,Cause.BATTLE,source.token,0);signal("ON_DESTROY",source,p);}}')
    edit('    private void nextPhase() {','    private void nextPhase() {\n        signal("ON_PHASE_END",null,null);\n        if(phase==Phase.END){signal("ON_TURN_END",null,null);expireEffects();}')
    edit('        note("Turn "+turn+" · "+phase);','        signal("ON_PHASE_START",null,null);if(phase==Phase.DRAW)signal("ON_TURN_START",null,null);\n        note("Turn "+turn+" · "+phase);')
    edit('private void damage(int actor,int amount) {life[actor]=Math.max(0,life[actor]-amount);if(life[actor]==0)winner=1-actor;}', '''private void damage(int actor,int amount) {
        if(pieces.values().stream().anyMatch(p->p.zone==Zone.FIELD&&p.controller==actor&&hasFlag(p,"PREVENT_DAMAGE"))){cue("SHIELD",null,"",actor,0);return;}
        boolean reflect=pieces.values().stream().anyMatch(p->p.zone==Zone.FIELD&&p.controller==actor&&hasFlag(p,"REFLECT_DAMAGE"));
        int recipient=reflect?1-actor:actor;life[recipient]=Math.max(0,life[recipient]-Math.max(0,amount));
        cue("DAMAGE",pieces.get(attacker),target,recipient,0);if(life[recipient]==0)winner=1-recipient;
    }''')
    edit('p.positionTurn=-1;}}','p.positionTurn=-1;p.flags.clear();p.fixedPower=-1;p.extraAttacks=0;p.permanentBoost=0;expiries.removeIf(expiry->expiry.token().equals(p.token));p.grantedEffect=null;p.controller=p.owner;}cueMove(p,from,to,cause,source,link);}')
    edit('p.zone!=Zone.DECK&&(p.zone!=Zone.HAND&&p.zone!=Zone.EXTRA||p.controller==viewer)', '(p.zone!=Zone.DECK||p.lookedBy==viewer)&&(p.zone!=Zone.HAND&&p.zone!=Zone.EXTRA||p.controller==viewer||revealed.contains(p.token))')
    edit('List.copyOf(log.subList(Math.max(0,log.size()-12),log.size())));', 'List.copyOf(log.subList(Math.max(0,log.size()-12),log.size())),visibleCues(viewer));',2)
    # spectatorView has no local viewer parameter.
    i=s.index('    public synchronized View spectatorView()')
    j=s.index('    /** Admin/QA',i)
    s=s[:i]+s[i:j].replace('visibleCues(viewer)','visibleCues(-1)')+s[j:]
    s=s.rstrip()[:-1]+(here/'duel_effects.inc').read_text()+'\n}\n'
p.write_text(s)
