package vn.svarcade.tcg.fabric;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.integration.*;
import com.google.gson.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
/** Reads the actual installed Cobblemon species data without initializing its Minecraft registry. */
public final class HeadlessCatalog {
 public static Catalog load()throws Exception {
  Catalog base=Catalog.load(Path.of("build/absent-performance-catalog.json"));URL url=Objects.requireNonNull(HeadlessCatalog.class.getResource("/data/cobblemon/species/generation1/charizard.json"));var jar=((JarURLConnection)url.openConnection()).getJarFile();List<CobblemonCatalogHydrator.SpeciesDescriptor> species=new ArrayList<>();
  for(var entry:Collections.list(jar.entries())){String n=entry.getName();if(!n.startsWith("data/cobblemon/species/")||!n.endsWith(".json"))continue;JsonObject j;try(var in=jar.getInputStream(entry)){j=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}if(!j.has("nationalPokedexNumber"))continue;String path=n.substring(n.lastIndexOf('/')+1,n.length()-5),name=j.get("name").getAsString(),type=j.get("primaryType").getAsString();int dex=j.get("nationalPokedexNumber").getAsInt(),bst=j.getAsJsonObject("baseStats").entrySet().stream().mapToInt(e->e.getValue().getAsInt()).sum();List<String> labels=j.has("labels")?j.getAsJsonArray("labels").asList().stream().map(JsonElement::getAsString).toList():List.of();species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:"+path,"cobblemon",path,name,dex,type,bst,List.of(),labels));
   if(j.has("forms"))for(JsonElement form:j.getAsJsonArray("forms")){var f=form.getAsJsonObject();if(!f.has("aspects"))continue;var aspects=f.getAsJsonArray("aspects").asList().stream().map(JsonElement::getAsString).toList();if(aspects.isEmpty())continue;String ft=f.has("primaryType")?f.get("primaryType").getAsString():type;int fb=f.has("baseStats")?f.getAsJsonObject("baseStats").entrySet().stream().mapToInt(e->e.getValue().getAsInt()).sum():bst;species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:"+path+"#"+String.join("_",aspects),"cobblemon",path,name+" · "+String.join(" ",aspects),dex,ft,fb,aspects,labels));}
  }
  Catalog c=CardIdentities.apply(CobblemonCatalogHydrator.expandFromDescriptors(base,species));var variants=SpecialAspectCards.definitions().stream().map(d->new ProviderAspects.Variant(d.species(),d.requiredAspects(),"mega_showdown","headless-data-fixture","",d.id())).toList();c=SpecialAspectCards.apply(c,new IntegrationCapabilities(Set.of("cobblemon","mega_showdown")),new ProviderAspects(variants),(s,a)->true);c=CardIdentities.apply(c);c.validate();return c;
 }
}
