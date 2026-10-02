package vn.svarcade.tcg.duel;
import vn.svarcade.tcg.performance.*;
import vn.svarcade.tcg.duel.Duel;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;
/** Same candidates, stable ranking, and difficulty randomness; legality remains Duel.act(). */
public final class BaselineAiPlanner {
    public static List<Duel.Action> plan(DuelReadSnapshot input,String difficulty,long seed){
        Duel.View v=input.view();int actor=v.you();Random rng=new Random(seed);
        Map<String,Catalog.Card> byName=new AbstractMap<>() {public Set<Entry<String,Catalog.Card>> entrySet(){return Set.of();}public Catalog.Card get(Object key){return input.definitions().values().stream().filter(c->c.name().equals(key)).findFirst().orElse(null);}};
        Map<String,Duel.VisibleCard> byToken=new AbstractMap<>() {public Set<Entry<String,Duel.VisibleCard>> entrySet(){return Set.of();}public Duel.VisibleCard get(Object key){return v.cards().stream().filter(c->c.token().equals(key)).findFirst().orElse(null);}};
        List<Duel.Action> actions=new ArrayList<>();
        List<Duel.VisibleCard> mine=v.cards().stream().filter(c->c.controller()==actor).toList();
        List<Duel.VisibleCard> field=mine.stream().filter(c->c.zone()==Duel.Zone.FIELD).toList();
        List<Duel.VisibleCard> enemy=v.cards().stream().filter(c->c.controller()!=actor&&c.zone()==Duel.Zone.FIELD).toList();

        if(v.open()&&v.turnPlayer()==actor&&(v.phase().equals("MAIN1")||v.phase().equals("MAIN2"))){
            for(var c:mine)if((c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.EXTRA)&&c.category().equals("pokemon")){
                actions.add(new Duel.Action("play",c.token(),""));
                for(var material:field)actions.add(new Duel.Action("play",c.token(),material.token()));
                for(int i=0;i<field.size();i++)for(int j=i+1;j<field.size();j++)
                    actions.add(new Duel.Action("play",c.token(),field.get(i).token()+","+field.get(j).token()));
            }
        }

        if(v.open()&&v.turnPlayer()==actor&&v.phase().equals("BATTLE")){
            for(var attacker:field){
                actions.add(new Duel.Action("attack",attacker.token(),""));
                for(var target:enemy)actions.add(new Duel.Action("attack",attacker.token(),target.token()));
            }
        }

        for(var c:mine)if(c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.FIELD){
            Catalog.Card d=byName.get(c.name());
            if(d==null||d.effect()==null)continue;
            String target=d.effect().target();
            if(target.equals("none"))actions.add(new Duel.Action("activate",c.token(),""));
            else if(target.equals("chain")&&!v.chain().isEmpty())actions.add(new Duel.Action("activate",c.token(),Integer.toString(v.chain().size())));
            else if(target.equals("enemy"))for(var t:enemy)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("ally"))for(var t:field)actions.add(new Duel.Action("activate",c.token(),t.token()));
            else if(target.equals("grave"))for(var t:mine)if(t.zone()==Duel.Zone.DISCARD)actions.add(new Duel.Action("activate",c.token(),t.token()));
        }

        if(v.open()&&v.turnPlayer()==actor)actions.add(new Duel.Action("next","",""));
        actions.add(new Duel.Action("pass","",""));

        if("EASY".equals(difficulty))Collections.shuffle(actions,rng);
        else{
            boolean hard="HARD".equals(difficulty);
            actions.sort(Comparator.comparingInt((Duel.Action action)->botScore(v,action,hard,byName,byToken)).reversed());
            if(!hard&&actions.size()>2&&rng.nextInt(100)<22)Collections.swap(actions,0,1+rng.nextInt(Math.min(3,actions.size()-1)));
        }

        return List.copyOf(actions);
    }

    private static int botScore(Duel.View v,Duel.Action action,boolean hard,Map<String,Catalog.Card> byName,Map<String,Duel.VisibleCard> byToken){
        if(action.kind().equals("pass"))return -100000;
        if(action.kind().equals("next"))return -50000;
        Duel.VisibleCard source=byToken.get(action.card());
        if(source==null)return -90000;
        Catalog.Card def=byName.get(source.name());
        if(action.kind().equals("play")){
            int score=1200+source.power()+(def==null?0:def.level()*90);
            if(!action.target().isBlank())for(String token:action.target().split(",")){
                Duel.VisibleCard tribute=byToken.get(token);
                if(tribute!=null)score-=hard?tribute.power()/2:tribute.power()/3;
            }
            return score;
        }
        if(action.kind().equals("attack")){
            if(action.target().isBlank())return 2600+source.power();
            Duel.VisibleCard target=byToken.get(action.target());
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
                Duel.VisibleCard target=byToken.get(action.target());
                if(target!=null&&target.controller()!=v.you())base+=hard?target.power()/2:target.power()/4;
            }
            return base;
        }
        return 0;
    }

}
