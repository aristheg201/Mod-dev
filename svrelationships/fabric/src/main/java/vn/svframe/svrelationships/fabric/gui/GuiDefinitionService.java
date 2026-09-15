package vn.svframe.svrelationships.fabric.gui;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class GuiDefinitionService {
    private final Path directory;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(0, Map.of()));

    public GuiDefinitionService(Path root) { this.directory = Objects.requireNonNull(root, "root").resolve("gui"); }

    public ReloadResult reload() {
        try {
            if (!Files.isDirectory(directory)) throw new IllegalArgumentException("GUI directory does not exist: " + directory);
            Map<String, GuiDefinition> definitions = new LinkedHashMap<>();
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                    GuiDefinition definition = parse(load(path));
                    if (definitions.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate GUI id: " + definition.id());
                }
            }
            current.set(new Snapshot(generation.incrementAndGet(), definitions));
            return new ReloadResult(true, "");
        } catch (Exception exception) { return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    }

    public Snapshot snapshot() { return current.get(); }

    private GuiDefinition parse(Map<String, Object> root) {
        String id=string(root,"id"),screen=string(root,"screen"),titleKey=string(root,"title_key");int capacity=screenCapacity(screen);
        Map<String,ComponentDefinition> components=new LinkedHashMap<>();
        for(var entry:map(root,"components").entrySet()){
            Map<String,Object> value=mapValue(entry.getValue(),"component "+entry.getKey());int slot=integer(value,"slot");validateSlot(id,slot,capacity);
            components.put(entry.getKey(),new ComponentDefinition(entry.getKey(),slot,string(value,"item"),string(value,"name_key"),stringList(value,"lore_keys"),string(value,"action"),stringMap(value,"args"),stringMap(value,"conditions")));
        }
        Map<String,RepeaterDefinition> repeaters=new LinkedHashMap<>();
        for(var entry:map(root,"repeaters").entrySet()){
            Map<String,Object> value=mapValue(entry.getValue(),"repeater "+entry.getKey());List<Integer> slots=integerList(value,"slots");if(slots.isEmpty())throw new IllegalArgumentException("Repeater has no slots: "+entry.getKey());
            for(int slot:slots){validateSlot(id,slot,capacity);if(components.values().stream().anyMatch(c->c.slot()==slot))throw new IllegalArgumentException("Repeater slot overlaps static component in GUI "+id+": "+slot);}
            repeaters.put(entry.getKey(),new RepeaterDefinition(entry.getKey(),slots,string(value,"source"),string(value,"item"),string(value,"name_key"),stringList(value,"lore_keys"),string(value,"action"),stringMap(value,"args"),stringMap(value,"conditions")));
        }
        return new GuiDefinition(id,screen,titleKey,components,repeaters);
    }

    private Map<String,Object> load(Path path)throws IOException{try(InputStream input=Files.newInputStream(path)){return mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input),path.getFileName().toString());}}
    private static void validateSlot(String id,int slot,int capacity){if(slot<0||slot>=capacity)throw new IllegalArgumentException("Invalid slot "+slot+" in GUI "+id+" with capacity "+capacity);}
    private static int screenCapacity(String id){return switch(id){case "generic_9x1"->9;case "generic_9x2"->18;case "generic_9x3"->27;case "generic_9x4"->36;case "generic_9x5"->45;case "hopper"->5;case "generic_3x3"->9;default->54;};}
    private static Map<String,Object> map(Map<String,Object>s,String k){Object value=s.get(k);return value==null?Map.of():mapValue(value,k);}private static Map<String,Object> mapValue(Object v,String l){if(v==null)return Map.of();if(!(v instanceof Map<?,?> raw))throw new IllegalArgumentException("Expected map at "+l);Map<String,Object> o=new LinkedHashMap<>();raw.forEach((k,x)->o.put(String.valueOf(k),x));return o;}private static String string(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)throw new IllegalArgumentException("Missing GUI key: "+k);return String.valueOf(v);}private static int integer(Map<String,Object>s,String k){Object v=s.get(k);return v instanceof Number n?n.intValue():Integer.parseInt(string(s,k));}private static List<String> stringList(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)return List.of();if(!(v instanceof List<?> list))throw new IllegalArgumentException("Expected list at "+k);return list.stream().map(String::valueOf).toList();}private static List<Integer> integerList(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)return List.of();if(!(v instanceof List<?> list))throw new IllegalArgumentException("Expected list at "+k);List<Integer> o=new ArrayList<>();for(Object x:list)o.add(x instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(x)));return List.copyOf(o);}private static Map<String,String> stringMap(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)return Map.of();Map<String,Object> raw=mapValue(v,k);Map<String,String> o=new LinkedHashMap<>();raw.forEach((a,b)->o.put(a,String.valueOf(b)));return o;}

    public record Snapshot(long generation,Map<String,GuiDefinition> definitions){public Snapshot{definitions=Map.copyOf(definitions);}}
    public record GuiDefinition(String id,String screen,String titleKey,Map<String,ComponentDefinition> components,Map<String,RepeaterDefinition> repeaters){public GuiDefinition{components=Map.copyOf(components);repeaters=Map.copyOf(repeaters);}}
    public record ComponentDefinition(String id,int slot,String itemId,String nameKey,List<String> loreKeys,String action,Map<String,String> args,Map<String,String> conditions){public ComponentDefinition{loreKeys=List.copyOf(loreKeys);args=Map.copyOf(args);conditions=Map.copyOf(conditions);}}
    public record RepeaterDefinition(String id,List<Integer> slots,String source,String itemId,String nameKey,List<String> loreKeys,String action,Map<String,String> args,Map<String,String> conditions){public RepeaterDefinition{slots=List.copyOf(slots);loreKeys=List.copyOf(loreKeys);args=Map.copyOf(args);conditions=Map.copyOf(conditions);}}
    public record ReloadResult(boolean success,String detail){}
}
