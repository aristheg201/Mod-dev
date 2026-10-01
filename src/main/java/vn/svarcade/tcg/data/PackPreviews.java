package vn.svarcade.tcg.data;

import java.util.*;

/** Display-only mascots selected once per catalog; never changes pulls or probabilities. */
public final class PackPreviews {
    private static final Map<String,String> AUTHORED=load();
    private static Map<String,String> load(){
        try(var in=PackPreviews.class.getResourceAsStream("/data/svarcade_tcg/pack_previews.json")){
            if(in==null)return Map.of();return new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8),new com.google.gson.reflect.TypeToken<Map<String,String>>(){}.getType());
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read pack preview data",e);}
    }
    public static Map<String,String> select(Catalog catalog){return select(catalog,AUTHORED);}
    static Map<String,String> select(Catalog catalog,Map<String,String> preferred){
        Map<String,String> result=new LinkedHashMap<>();Set<String> used=new HashSet<>();
        var all=catalog.cards().values().stream().filter(PackPreviews::pokemon).sorted(order()).toList();
        for(var entry:catalog.banners().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            var pool=entry.getValue().pool();Set<String> featured=new HashSet<>();
            pool.stream().filter(Catalog.Weighted::featured).forEach(w->featured.add(w.card()));
            var candidates=pool.stream().map(w->catalog.cards().get(w.card())).filter(PackPreviews::pokemon).distinct()
                    .sorted(Comparator.<Catalog.Card,Boolean>comparing(c->!featured.contains(c.id())).thenComparing(order())).toList();
            // Support-only packs still get an artwork mascot, explicitly separate from their rate list.
            if(candidates.isEmpty())candidates=all;
            String authored=preferred.get(entry.getKey());
            var chosen=candidates.stream().filter(c->c.id().equals(authored)&&!used.contains(identity(c))).findFirst().orElse(null);
            if(chosen==null)chosen=candidates.stream().filter(c->!used.contains(identity(c))).findFirst().orElse(candidates.isEmpty()?null:candidates.getFirst());
            if(chosen!=null){used.add(identity(chosen));result.put(entry.getKey(),chosen.id());}
        }
        return Collections.unmodifiableMap(result);
    }
    private static String identity(Catalog.Card c){return c.species().contains(":")?c.species():"cobblemon:"+c.species();}
    private static boolean pokemon(Catalog.Card c){return c!=null&&"pokemon".equals(c.category())&&c.species()!=null&&!c.species().isBlank();}
    private static Comparator<Catalog.Card> order(){return Comparator.comparingInt(Catalog.Card::level).reversed().thenComparing(Comparator.comparingInt(Catalog.Card::power).reversed()).thenComparing(Catalog.Card::id);}
    private PackPreviews(){}
}
