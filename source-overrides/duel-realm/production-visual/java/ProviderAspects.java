package vn.svarcade.tcg.integration;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Reads actual provider resolver declarations without loading client model classes. */
public final class ProviderAspects {
    public record Variant(String species,List<String> aspects,String provider,String model,String poser,String resource) {}
    private final Map<String,Variant> variants;
    public ProviderAspects(Collection<Variant> values) {
        Map<String,Variant> unique=new LinkedHashMap<>();
        for(var value:values)unique.put(key(value.species(),value.aspects()),value);
        variants=Map.copyOf(unique);
    }
    public static String species(String raw) {
        String id=raw.trim().toLowerCase(Locale.ROOT);return id.contains(":")?id:"cobblemon:"+id;
    }
    public static String key(String species,Collection<String> aspects) { return species(species)+"#"+String.join("+",new TreeSet<>(aspects)); }
    public Collection<Variant> variants() { return variants.values(); }
    public Optional<Variant> find(String species,Collection<String> aspects) {
        return Optional.ofNullable(variants.get(key(species,aspects)));
    }
    public static ProviderAspects discover(Map<String,List<Path>> roots) {
        List<Variant> result=new ArrayList<>();Set<String> models=new HashSet<>();
        roots.values().forEach(paths->{for(Path root:paths){Path assets=root.resolve("assets");if(!Files.isDirectory(assets))continue;
            try(var namespaces=Files.list(assets)){for(Path ns:namespaces.toList()){Path dir=ns.resolve("bedrock/pokemon/models");if(!Files.isDirectory(dir))continue;
                try(var files=Files.walk(dir)){files.filter(p->p.toString().endsWith(".json")).forEach(p->models.add(ns.getFileName()+":"+p.getFileName().toString().replaceFirst("\\.json$","")));}
            }}catch(java.io.IOException e){throw new IllegalStateException(e);}
        }});
        roots.forEach((provider,paths)->{for(Path root:paths){
            Path assets=root.resolve("assets");if(!Files.isDirectory(assets))continue;
            try(var namespaces=Files.list(assets)) {
                for(Path namespace:namespaces.toList()) {
                    Path resolvers=namespace.resolve("bedrock/pokemon/resolvers");if(!Files.isDirectory(resolvers))continue;
                    try(var files=Files.walk(resolvers)) {
                        for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                            try(var reader=Files.newBufferedReader(file)) {
                                JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
                                if(!json.has("species")||!json.has("variations"))continue;
                                String model="",poser="";
                                for(var raw:json.getAsJsonArray("variations")) {
                                    JsonObject v=raw.getAsJsonObject();List<String> aspects=new ArrayList<>();
                                    if(v.has("aspects"))for(var a:v.getAsJsonArray("aspects"))aspects.add(a.getAsString());
                                    if(v.has("model"))model=v.get("model").getAsString();
                                    if(v.has("poser"))poser=v.get("poser").getAsString();
                                    if(model.isBlank())continue;
                                    String[] m=model.split(":",2);String ns=m.length==2?m[0]:namespace.getFileName().toString(),path=m.length==2?m[1]:m[0];
                                    boolean exists=models.contains(ns+":"+path);
                                    if(exists)result.add(new Variant(species(json.get("species").getAsString()),List.copyOf(aspects),provider,model,poser,file.toString()));
                                }
                            } catch(Exception e) { throw new IllegalStateException("Invalid provider resolver "+provider+":"+file,e); }
                        }
                    }
                }
            }catch(java.io.IOException e){throw new IllegalStateException("Unable to discover provider "+provider,e);}
        }});
        return new ProviderAspects(result);
    }
}
