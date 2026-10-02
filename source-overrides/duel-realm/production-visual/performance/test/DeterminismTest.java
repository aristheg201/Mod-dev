package vn.svarcade.tcg.duel;
import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.fabric.HeadlessCatalog;
import vn.svarcade.tcg.performance.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
/** Compare the frozen current-HEAD engine with the indexed engine after EVERY command, including rejected ones. */
public class DeterminismTest {
 static final Gson JSON=new Gson();static Catalog catalog;
 @BeforeAll static void load()throws Exception{catalog=HeadlessCatalog.load();assertTrue(catalog.cards().size()>1025);}
 static List<String> deck(Catalog c){return c.starters().get("crossroads");}
 static Duel fixture(Catalog c,int seed){Duel d=new Duel(c,deck(c),List.of(),deck(c),List.of(),new Random(seed));populate(d,c);d.qaSeal();return d;}
 static BaselineDuel baseline(Catalog c,int seed){var d=new BaselineDuel(c,deck(c),List.of(),deck(c),List.of(),new Random(seed));populate(d,c);d.registeredEffects();return d;}
 static void populate(Object d,Catalog c){try{
  Method reset=d.getClass().getDeclaredMethod("qaReset",int.class,int.class);reset.setAccessible(true);reset.invoke(d,0,3);
  Class<?> zone=Class.forName(d.getClass().getName()+"$Zone");Method add=d.getClass().getDeclaredMethod("qaAdd",int.class,String.class,zone);add.setAccessible(true);
  for(int seat=0;seat<2;seat++){
   for(String id:deck(c))add.invoke(d,seat,id,Enum.valueOf((Class)zone,"DECK"));
   for(String id:List.of("charmander","squirtle","bulbasaur"))add.invoke(d,seat,id,Enum.valueOf((Class)zone,"FIELD"));
   String special=SpecialAspectCards.definitions().get(seat).id();Object p=add.invoke(d,seat,special,Enum.valueOf((Class)zone,"FIELD"));var counters=(Map<String,Integer>)field(p,"counters");for(var cost:c.card(special).effect().spec().costs())if(cost.counter()!=null)counters.put(cost.counter(),3);
   for(String id:List.of("flamethrower","shatter_gate","void_seal","stellar_filter","grave_bloom","recruit_signal","potion","protect"))add.invoke(d,seat,id,Enum.valueOf((Class)zone,"HAND"));
   for(String id:List.of("charmander","potion"))add.invoke(d,seat,id,Enum.valueOf((Class)zone,"DISCARD"));
   for(String id:List.of("counter_seal","mirror_barrier")){Object p2=add.invoke(d,seat,id,Enum.valueOf((Class)zone,"SUPPORT"));String token=(String)field(p2,"token");((Set<String>)field(d,"faceDown")).add(token);((Map<String,Integer>)field(d,"setTurn")).put(token,1);}
  }
  Object aura=add.invoke(d,0,"ember_domain",Enum.valueOf((Class)zone,"STADIUM"));((Set<String>)field(d,"activatedContinuous")).add((String)field(aura,"token"));
 }catch(Exception e){throw new AssertionError(e);}}
 static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 static final List<String> FIELDS=List.of("life","normal","turnPlayer","priority","turn","passes","winner","revision","phase","open","advance","attacker","target","chain","history","pendingEvents","log","used","faceDown","setTurn","summonProgress","cues","cueSequence","activatedContinuous","expiries","revealed","summonLocks","optionalTriggers","stageUsed","stageDuelUsed","duelUsed");
 static final Pattern UUIDS=Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
 static final class Tokens {final Map<String,String> values=new LinkedHashMap<>();void observe(Object d){for(Object p:((Map<?,?>)field(d,"pieces")).values()){String token=(String)field(p,"token");values.computeIfAbsent(token,t->"token-"+values.size());}}String normalize(String text){return UUIDS.matcher(text).replaceAll(m->values.getOrDefault(m.group(),m.group()));}}
 static String state(Object d,Tokens tokens){tokens.observe(d);Map<String,Object> state=new TreeMap<>();for(String name:FIELDS)state.put(name,field(d,name) instanceof Set<?> set?set.stream().map(JSON::toJson).map(tokens::normalize).sorted().toList():field(d,name));Map<String,Object> pieces=new LinkedHashMap<>();for(var entry:((Map<?,?>)field(d,"pieces")).entrySet()){Object p=entry.getValue();Map<String,Object> mutable=new TreeMap<>();for(var f:p.getClass().getDeclaredFields()){if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);try{Object value=f.get(p);mutable.put(f.getName(),value instanceof Catalog.Card c?c.id():value);}catch(Exception e){throw new AssertionError(e);}}pieces.put(entry.getKey().toString(),mutable);}state.put("pieces",pieces);state.put("pieceOrder",pieces.keySet().stream().toList());
  Object delayed=field(d,"delayed");List<Object> all=new ArrayList<>();if(delayed instanceof Map<?,?> map)for(Object bucket:map.values())all.addAll((List<?>)bucket);else all.addAll((List<?>)delayed);List<String> scheduled=all.stream().map(JSON::toJson).map(tokens::normalize).sorted().toList();state.put("delayed",scheduled);return canonical(com.google.gson.JsonParser.parseString(tokens.normalize(JSON.toJson(state))));}
 static String canonical(com.google.gson.JsonElement e){if(e.isJsonObject()){var map=new TreeMap<String,String>();e.getAsJsonObject().entrySet().forEach(x->map.put(x.getKey(),canonical(x.getValue())));return map.toString();}if(e.isJsonArray())return e.getAsJsonArray().asList().stream().map(DeterminismTest::canonical).toList().toString();return e.toString();}
 static Duel.View view(BaselineDuel d,int seat){return JSON.fromJson(JSON.toJson(d.view(seat)),Duel.View.class);}
 static String translate(String input,Tokens source,Tokens destination){Map<String,String> reverse=new HashMap<>();destination.values.forEach((k,v)->reverse.put(v,k));return UUIDS.matcher(input).replaceAll(m->reverse.get(source.values.get(m.group())));}
 @Test void scriptedAndAsyncPlanningMatchFrozenEngineAfterEveryAction()throws Exception {
  try(var workers=new CardWorldsExecutors(PerformanceConfig.defaults())){for(int seed=0;seed<8;seed++){
   Duel d=fixture(catalog,seed);BaselineDuel old=baseline(catalog,seed);Tokens now=new Tokens(),before=new Tokens();assertEquals(state(old,before),state(d,now));
   for(int round=0;round<160&&d.winner()<0;round++){int actor=d.priority();long random=seed*100000L+round;String difficulty=(round+seed)%2==0?"HARD":"NORMAL";Duel.View v=d.view(actor);var input=new DuelReadSnapshot("determinism-"+seed,v.revision(),v,catalog.cards());
    List<Duel.Action> actions=workers.submit(CardWorldsExecutors.Kind.CPU,new CardWorldsExecutors.Context("determinism",input.duelId(),input.revision()),()->AiPlanner.plan(input,difficulty,random)).get();
    List<Duel.Action> baselineActions=BaselineAiPlanner.plan(new DuelReadSnapshot("baseline",old.revision(),view(old,actor),catalog.cards()),difficulty,random);
    assertEquals(before.normalize(JSON.toJson(baselineActions)),now.normalize(JSON.toJson(actions)),"Every AI candidate and its order must match");
    boolean applied=false;for(var action:actions){String card=translate(action.card(),now,before),target=translate(action.target(),now,before);String error=null;try{d.act(actor,action,d.revision());applied=true;}catch(IllegalArgumentException e){error=e.getMessage();}String baselineError=null;try{old.act(actor,new BaselineDuel.Action(action.kind(),card,target),old.revision());}catch(IllegalArgumentException e){baselineError=e.getMessage();}assertEquals(baselineError,error,"Validation result changed");assertEquals(state(old,before),state(d,now),"Authoritative state changed seed="+seed+" round="+round+" action="+action);if(applied)break;}assertTrue(applied,"AI must eventually select a legal action");
   }
  }}
 }
 @Test void mutableStateRejectsWorkerAccess()throws Exception{Duel d=fixture(catalog,1);try(var workers=new CardWorldsExecutors(PerformanceConfig.defaults())){var f=workers.submit(CardWorldsExecutors.Kind.CPU,new CardWorldsExecutors.Context("unsafe-test","duel",d.revision()),()->{d.act(d.priority(),new Duel.Action("pass","",""),d.revision());return true;});assertThrows(java.util.concurrent.ExecutionException.class,f::get);}}
}
