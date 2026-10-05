package vn.worldcomesalive.furniture;
import java.io.*;
import java.util.*;
import com.google.gson.Gson;
/** Curated cluster grammar and style kits are datapack content, independent of block models. */
public final class FurnishingContent {
    public record Style(String id,String region,String wealth,String wood,String seat,String storage,int decorationBudget,double density,String lighting,String tableware){}
    public record Atom(String type,int x,int y,int z,String facing,String layer,List<String> markers){}
    public List<Style> styles;
    public Map<String,List<Atom>> clusters;
    public Map<String,List<String>> variants;
    public static volatile FurnishingContent active=defaults();
    public static FurnishingContent defaults(){try(var in=FurnishingContent.class.getResourceAsStream("/data/worldcomesalive/living_world/furnishing.json")){return read(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new UncheckedIOException(e);}}
    public static FurnishingContent read(Reader reader){var c=new Gson().fromJson(reader,FurnishingContent.class);if(c==null||c.styles==null||c.styles.isEmpty()||c.clusters==null||!c.clusters.containsKey("master_bedroom")||c.variants==null)throw new IllegalArgumentException("Incomplete furnishing grammar");for(var style:c.styles)if(style.decorationBudget<8||style.decorationBudget>160||style.density<.15||style.density>.85)throw new IllegalArgumentException("Invalid furnishing budget");for(var atoms:c.clusters.values())for(var atom:atoms)if(atom.markers==null||!Set.of("primary","secondary","detail").contains(atom.layer))throw new IllegalArgumentException("Invalid furnishing atom");return c;}
    public Style style(String region,double wealth){String tier=wealth<.45?"poor":wealth>.76?"wealthy":"common";return styles.stream().filter(s->s.region.equals(region)&&s.wealth.equals(tier)).findFirst().orElseGet(()->styles.stream().filter(s->s.wealth.equals(tier)).findFirst().orElse(styles.getFirst()));}
}
