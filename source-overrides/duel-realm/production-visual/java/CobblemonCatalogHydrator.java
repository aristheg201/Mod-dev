package vn.svarcade.tcg.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svarcade.tcg.data.Catalog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * Expands the authored TCG catalog with every species currently registered by
 * Cobblemon. Custom namespaces are treated as addon/fakemon content, so the TCG
 * follows the actual server registry instead of freezing a hard-coded species list.
 */
public final class CobblemonCatalogHydrator {
    private static final Logger LOG=LoggerFactory.getLogger("cardworlds-catalog");
    private record Family(String id, String name, List<String> preferred, List<String> types, List<String> tags, List<String> aspects,
                          int minDex, int maxDex, int minBst, boolean forms, boolean pack) {}
    private record DeckContent(List<Family> families, Map<String,String> aliases, List<String> supports) {}
    private static DeckContent content() {
        try (var stream = CobblemonCatalogHydrator.class.getResourceAsStream("/data/svarcade_tcg/deck_families.json")) {
            if (stream == null) throw new IllegalStateException("Missing deck family content");
            return new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8),DeckContent.class);
        } catch (java.io.IOException e) { throw new IllegalStateException("Unable to load deck content",e); }
    }

    record SpeciesDescriptor(String identifier,String namespace,String path,String name,int dex,String type,int bst,List<String> aspects, List<String> tags) {
        SpeciesDescriptor(String identifier,String namespace,String path,String name,int dex,String type,int bst){this(identifier,namespace,path,name,dex,type,bst,List.of(),List.of());}
        SpeciesDescriptor(String identifier,String namespace,String path,String name,int dex,String type,int bst,List<String> aspects){this(identifier,namespace,path,name,dex,type,bst,aspects,List.of());}
    }

    private CobblemonCatalogHydrator() {}

    public static Catalog expand(Catalog base) {
        List<SpeciesDescriptor> species=readCobblemonSpecies();
        Catalog expanded=expandFromDescriptors(base,species);
        long fakemon=species.stream().filter(s->isAddon(s)&&s.aspects().isEmpty()).map(s->s.namespace()+":"+s.path()).distinct().count();
        long forms=species.stream().filter(s->!s.aspects().isEmpty()).count();
        long bases=species.stream().map(s->s.namespace()+":"+s.path()).distinct().count();
        LOG.info("CARDWORLDS_CATALOG_HYDRATED cards={} species={} fakemon={} decks={} banners={}",
            expanded.cards().size(),bases,fakemon,expanded.starters().size(),expanded.banners().size());
        long officialBases=species.stream().filter(s -> !isAddon(s)).map(s->s.namespace()+":"+s.path()).distinct().count();
        LOG.info("CARDWORLDS_FORMS_HYDRATED baseSpecies={} forms={}",officialBases,forms);
        LOG.info("CARDWORLDS_EFFECT_COVERAGE primitives={} nontrivial={}",vn.svarcade.tcg.duel.Duel.effectPrimitiveCount(),expanded.cards().values().stream().filter(c->c.effect()!=null&&c.effect().spec()!=null&&c.effect().spec().operations().stream().anyMatch(op->!Set.of("DAMAGE_LP","HEAL_LP","DRAW").contains(op.type()))).count());
        if (Boolean.getBoolean("cardworlds.qa.requireFakemon") && fakemon == 0) throw new IllegalStateException("QA requires real addon species, but none registered");
        LOG.info("CARDWORLDS_DECK_FAMILIES unique={} aliases={}",expanded.starters().values().stream().map(d -> new TreeMap<>(d.stream().collect(java.util.stream.Collectors.groupingBy(id -> id,java.util.stream.Collectors.counting())))).distinct().count(),content().aliases().size());
        return expanded;
    }

    static Catalog expandFromDescriptors(Catalog base,List<SpeciesDescriptor> descriptors) {
        LinkedHashMap<String,Catalog.Card> cards=new LinkedHashMap<>(base.cards());
        LinkedHashMap<String,Catalog.Banner> banners=new LinkedHashMap<>(base.banners());
        LinkedHashMap<String,List<String>> starters=new LinkedHashMap<>(base.starters());
        List<SpeciesDescriptor> sorted=new ArrayList<>(descriptors);
        sorted.sort(Comparator.comparingInt((SpeciesDescriptor d)->d.dex()<=0?Integer.MAX_VALUE:d.dex()).thenComparing(SpeciesDescriptor::identifier));

        LinkedHashMap<String,String> cardByIdentifier=new LinkedHashMap<>();
        LinkedHashMap<String,SpeciesDescriptor> descriptorByCard=new LinkedHashMap<>();
        for(SpeciesDescriptor d:sorted){
            String cardId=cardId(d);
            Catalog.Card existing=cards.get(cardId);
            if(existing==null){
                int level=level(d.bst());
                int power=power(d.bst(),level);
                String runtimeSpecies=d.namespace().equals("cobblemon")?d.path():d.identifier();
                existing=new Catalog.Card(cardId,d.name(),"pokemon",runtimeSpecies,d.aspects(),normalizeType(d.type()),
                    !isAddon(d)?regionName(d.dex()):"fakemon:"+d.namespace(),"",false,level,power,
                    "Registry Pokémon card generated from the active Cobblemon species data.",
                    !isAddon(d)?"National Dex":"Addon · "+d.namespace(),rarity(level),List.of("PACK"),null,List.of(),List.of());
                cards.put(cardId,existing);
            }
            cardByIdentifier.put(d.identifier(),cardId);
            descriptorByCard.put(cardId,d);
        }

        DeckContent content=content();
        for(Family family:content.families()) {
            LinkedHashSet<String> ids=new LinkedHashSet<>();
            for(String path:family.preferred()) descriptorByCard.forEach((id,d)->{if(d.path().equals(path))ids.add(id);});
            descriptorByCard.forEach((id,d)->{
                boolean selected=family.types().contains(normalizeType(d.type())) || d.tags().stream().anyMatch(family.tags()::contains)
                    || d.aspects().stream().anyMatch(a -> family.aspects().stream().anyMatch(w -> a.equals(w)||a.startsWith(w+"-")||a.startsWith(w+"_")))
                    || (family.forms()&&!d.aspects().isEmpty()) || (family.minBst()>0&&d.bst()>=family.minBst())
                    || (family.maxDex()>0&&!isAddon(d)&&d.dex()>=family.minDex()&&d.dex()<=family.maxDex());
                if(selected)ids.add(id);
            });
            if(ids.isEmpty()) continue;
            starters.put(family.id(),makeDeck(new ArrayList<>(ids),cards));
            if(family.pack()) banners.put(family.id()+"_pack",banner(family.name(),family.id(),new ArrayList<>(ids),cards));
        }
        Set<String> types=new TreeSet<>();descriptorByCard.values().forEach(d->types.add(normalizeType(d.type())));
        for(String type:types){
            List<String> ids=filterCards(descriptorByCard,d->normalizeType(d.type()).equals(type));
            if(ids.size()>=8)starters.put("type_"+safe(type),makeDeck(ids,cards));
        }
        content.aliases().forEach((alias,source)->alias(starters,alias,source));

        // One deck + pack per custom namespace. This is the fakemon/addon bridge.
        Map<String,List<String>> byNamespace=new TreeMap<>();
        descriptorByCard.forEach((id,d)->{if(isAddon(d))byNamespace.computeIfAbsent(d.namespace(),k->new ArrayList<>()).add(id);});
        byNamespace.forEach((ns,ids)->{
            starters.put("fakemon_"+safe(ns),makeDeck(ids,cards));
            banners.put("fakemon_"+safe(ns)+"_pack",banner("Fakemon · "+ns,"fakemon_"+safe(ns),ids,cards));
        });
        List<String> allFakemon=byNamespace.values().stream().flatMap(Collection::stream).distinct().toList();
        if(!allFakemon.isEmpty()){
            starters.put("fakemon_all",makeDeck(allFakemon,cards));
            banners.put("fakemon_all_pack",banner("Fakemon Nexus","fakemon_all",allFakemon,cards));
        }

        Catalog result=new Catalog(base.rules(),cards,banners,base.rewards(),base.dealers(),starters);
        result=vn.svarcade.tcg.data.EffectContent.apply(result);
        result.validate();
        return result;
    }

    private static void alias(Map<String,List<String>> starters,String alias,String source){List<String> deck=starters.get(source);if(deck!=null)starters.put(alias,deck);}

    private static Catalog.Banner banner(String name,String family,List<String> ids,Map<String,Catalog.Card> cards){
        List<Catalog.Weighted> pool=new ArrayList<>();
        for(int i=0;i<ids.size();i++){
            Catalog.Card c=cards.get(ids.get(i));if(c==null||!c.sources().contains("PACK"))continue;
            boolean high=c.level()>=7 || i==ids.size()-1;
            pool.add(new Catalog.Weighted(c.id(),Math.max(1,13-c.level()),high,high));
        }
        if(pool.isEmpty())throw new IllegalArgumentException("Empty generated banner "+family);
        return new Catalog.Banner(name,family,"PACK",800,5,80,60,3,0,Long.MAX_VALUE,List.copyOf(pool));
    }
    private static List<String> filterCards(Map<String,SpeciesDescriptor> descriptors,Predicate<SpeciesDescriptor> predicate){
        return descriptors.entrySet().stream().filter(e->predicate.test(e.getValue())).sorted(Comparator.comparingInt(e->e.getValue().dex()<=0?Integer.MAX_VALUE:e.getValue().dex())).map(Map.Entry::getKey).toList();
    }
    private static List<String> makeDeck(List<String> candidates,Map<String,Catalog.Card> cards){
        List<String> legal=candidates.stream().filter(cards::containsKey).filter(id->{var c=cards.get(id);return c.category().equals("pokemon")&&!c.extra();}).distinct().toList();
        Set<String> identityTypes=new HashSet<>();legal.forEach(id->identityTypes.add(cards.get(id).type()));
        LinkedHashSet<String> setup=new LinkedHashSet<>(legal.stream().filter(id->cards.get(id).level()<=4).toList());
        cards.values().stream().filter(c->c.category().equals("pokemon")&&!c.extra()&&c.level()<=4&&identityTypes.contains(c.type()))
            .sorted(Comparator.comparing(Catalog.Card::id)).map(Catalog.Card::id).forEach(setup::add);
        List<String> deck=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        addCopies(deck,counts,new ArrayList<>(setup),cards,12);
        addCopies(deck,counts,legal.stream().filter(id->cards.get(id).level()>4&&cards.get(id).level()<=6).toList(),cards,Math.min(20,deck.size()+8));
        addCopies(deck,counts,legal.stream().filter(id->cards.get(id).level()>6).toList(),cards,Math.min(24,deck.size()+4));
        addCopies(deck,counts,legal.stream().filter(id->cards.get(id).level()<=6).toList(),cards,24);
        addCopies(deck,counts,new ArrayList<>(setup),cards,24);
        addCopies(deck,counts,legal,cards,24);
        // Support cards with matching continuous modifiers lead the support package.
        LinkedHashSet<String> supports=new LinkedHashSet<>();
        cards.values().stream().filter(c->!c.extra()&&!c.category().equals("pokemon")&&c.modifiers()!=null
            &&c.modifiers().stream().anyMatch(m->identityTypes.contains(m.affectedType())))
            .map(Catalog.Card::id).forEach(supports::add);
        supports.addAll(content().supports());
        cards.values().stream().filter(c->!c.extra()&&!c.category().equals("pokemon")).map(Catalog.Card::id).forEach(supports::add);
        addCopies(deck,counts,new ArrayList<>(supports),cards,40);
        if(deck.size()!=40)throw new IllegalStateException("Unable to construct balanced generated deck");
        return List.copyOf(deck);
    }
    private static void addCopies(List<String> deck,Map<String,Integer> counts,List<String> ids,Map<String,Catalog.Card> cards,int target){
        for(int pass=0;pass<3&&deck.size()<target;pass++)for(String id:ids) {
            if(deck.size()>=target)break;
            if(cards.containsKey(id)&&!cards.get(id).extra()&&counts.getOrDefault(id,0)<3){deck.add(id);counts.merge(id,1,Integer::sum);}
        }
    }
    private static boolean isAddon(SpeciesDescriptor d){return !d.namespace().equals("cobblemon")||d.dex()<=0||d.dex()>1025;}

    private static int level(int bst){if(bst<=0)return 4;if(bst<330)return 3;if(bst<430)return 4;if(bst<500)return 5;if(bst<560)return 6;if(bst<620)return 7;return 8;}
    private static int power(int bst,int level){return Math.clamp(bst>0?650+bst*3:900+level*220,900,3200);}
    private static String rarity(int level){return level>=8?"Secret":level>=7?"Ultra Rare":level>=6?"Super Rare":level>=5?"Rare":"Common";}
    private static String cardId(SpeciesDescriptor d){String base=d.namespace().equals("cobblemon")?safe(d.path()):"fakemon__"+safe(d.namespace())+"__"+safe(d.path());return d.aspects().isEmpty()?base:base+"__form__"+safe(String.join("_",new TreeSet<>(d.aspects())));}
    private static String safe(String raw){return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_").replaceAll("_+","_").replaceAll("^_|_$","");}
    private static String title(String raw){String[] parts=raw.split("_");StringBuilder b=new StringBuilder();for(String p:parts){if(p.isBlank())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
    private static String normalizeType(String type){String v=type==null?"normal":type.toLowerCase(Locale.ROOT);int dot=v.lastIndexOf('.');if(dot>=0)v=v.substring(dot+1);return safe(v.isBlank()?"normal":v);}
    private static String regionName(int dex){if(dex<=0)return "Addon";if(dex<=151)return "Kanto";if(dex<=251)return "Johto";if(dex<=386)return "Hoenn";if(dex<=493)return "Sinnoh";if(dex<=649)return "Unova";if(dex<=721)return "Kalos";if(dex<=809)return "Alola";if(dex<=905)return "Galar / Hisui";return "Paldea";}

    private static List<SpeciesDescriptor> readCobblemonSpecies(){
        try{
            Class<?> registry=Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");Object raw=invokeRegistry(registry,"getSpecies");
            if(!(raw instanceof Collection<?> collection))throw new IllegalStateException("PokemonSpecies.getSpecies did not return a collection");
            LinkedHashMap<String,Object> loaded=new LinkedHashMap<>();
            for(Object species:collection)loaded.put(string(call(species,"getResourceIdentifier")),species);
            Object namespaces=invokeRegistry(registry,"getNamespaces");
            if(namespaces instanceof Collection<?> all)for(Object namespace:all){
                Object values=registry.getMethod("getSpeciesInNamespace",String.class).invoke(null,String.valueOf(namespace));
                if(values instanceof Map<?,?> entries)for(Object species:entries.values())loaded.put(string(call(species,"getResourceIdentifier")),species);
            }
            List<SpeciesDescriptor> result=new ArrayList<>();
            for(Object species:loaded.values()){
                String identifier=string(call(species,"getResourceIdentifier"));if(identifier.isBlank())continue;
                String[] split=identifier.split(":",2);String namespace=split.length==2?split[0]:"cobblemon",path=split.length==2?split[1]:split[0];
                String name=string(call(species,"getName"));if(name.isBlank())name=title(path);
                int dex=number(call(species,"getNationalPokedexNumber"));String type=typeName(call(species,"getPrimaryType"));int bst=readBst(call(species,"getBaseStats"));
                result.add(new SpeciesDescriptor(identifier,namespace,path,name,dex,type,bst,List.of(),stringList(call(species,"getLabels"))));
                Object formsRaw=call(species,"getForms");
                if(formsRaw instanceof Collection<?> forms){
                    Set<String> seenForms=new HashSet<>();
                    for(Object form:forms){
                        List<String> aspects=stringList(call(form,"getAspects"));
                        if(aspects.isEmpty())continue;
                        List<String> normalized=aspects.stream().filter(v->v!=null&&!v.isBlank()).map(v->v.toLowerCase(Locale.ROOT)).distinct().sorted().toList();
                        if(normalized.isEmpty())continue;
                        String formKey=String.join("+",normalized);if(!seenForms.add(formKey))continue;
                        String formName=string(call(form,"getName"));if(formName.isBlank())formName=title(formKey.replace('+','_'));
                        String formType=typeName(call(form,"getPrimaryType"));if(formType.equals("normal")&&!type.equals("normal"))formType=type;
                        int formBst=readBst(call(form,"getBaseStats"));if(formBst<=0)formBst=bst;
                        result.add(new SpeciesDescriptor(identifier+"#"+formKey,namespace,path,name+" · "+formName,dex,formType,formBst,normalized,stringList(call(form,"getLabels"))));
                    }
                }
            }
            return List.copyOf(result);
        }catch(Exception ex){throw new IllegalStateException("Unable to hydrate Card Worlds from Cobblemon species registry",ex);}
    }
    private static Object invokeRegistry(Class<?> type,String method)throws Exception{
        try{Method m=type.getMethod(method);return m.invoke(null);}catch(ReflectiveOperationException ignored){Field f=type.getField("INSTANCE");Object instance=f.get(null);return type.getMethod(method).invoke(instance);}
    }
    private static Object call(Object target,String method){if(target==null)return null;try{return target.getClass().getMethod(method).invoke(target);}catch(Exception ignored){return null;}}
    private static List<String> stringList(Object value){
        if(!(value instanceof Collection<?> c))return List.of();
        return c.stream().map(String::valueOf).filter(v->!v.isBlank()).toList();
    }
    private static String string(Object value){return value==null?"":String.valueOf(value);}
    private static int number(Object value){return value instanceof Number n?n.intValue():0;}
    private static String typeName(Object value){if(value==null)return "normal";Object name=call(value,"getName");return normalizeType(name==null?String.valueOf(value):String.valueOf(name));}
    private static int readBst(Object stats){
        if(stats==null)return 0;
        if(stats instanceof Map<?,?> map) {
            List<Integer> values=map.values().stream().filter(Number.class::isInstance).map(v->((Number)v).intValue()).toList();
            if(values.size()==6&&values.stream().allMatch(v->v>0))return values.stream().mapToInt(Integer::intValue).sum();
        }
        int hp=stat(stats,"getHp"),attack=stat(stats,"getAttack"),defence=stat(stats,"getDefence","getDefense"),
            specialAttack=stat(stats,"getSpecialAttack"),specialDefence=stat(stats,"getSpecialDefence","getSpecialDefense"),speed=stat(stats,"getSpeed");
        if(hp<=0||attack<=0||defence<=0||specialAttack<=0||specialDefence<=0||speed<=0)return 0;
        return hp+attack+defence+specialAttack+specialDefence+speed;
    }
    private static int stat(Object stats,String... methods){for(String method:methods){Object value=call(stats,method);if(value instanceof Number n&&n.intValue()>0)return n.intValue();}return 0;}
}
