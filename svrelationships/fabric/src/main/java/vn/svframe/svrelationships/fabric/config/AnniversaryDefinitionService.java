package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.gameplay.AnniversaryDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AnniversaryDefinitionService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public AnniversaryDefinitionService(Path root) { this.root = Objects.requireNonNull(root, "root"); }

    public void initialize() {
        try {
            Path target = root.resolve("anniversaries.yml");
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream input = AnniversaryDefinitionService.class.getResourceAsStream("/defaults/anniversaries.yml")) {
                    if (input == null) throw new IOException("Missing bundled resource: /defaults/anniversaries.yml");
                    Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) { throw new IllegalStateException("Unable to install anniversary definitions", exception); }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load anniversary definitions: " + result.detail());
    }

    public Snapshot snapshot() { return Objects.requireNonNull(current.get(), "anniversaries not initialized"); }

    public ReloadResult reload() {
        try {
            Map<String,Object> rootMap;
            try (InputStream input = Files.newInputStream(root.resolve("anniversaries.yml"))) {
                rootMap = mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input), "anniversaries.yml");
            }
            Map<String,AnniversaryDefinition> definitions = new LinkedHashMap<>();
            for (var entry : map(rootMap,"anniversaries").entrySet()) {
                Map<String,Object> value = mapValue(entry.getValue(),"anniversary "+entry.getKey());
                definitions.put(entry.getKey(), new AnniversaryDefinition(
                        entry.getKey(), GameplayDefinitionService.durationMillis(string(value,"period")),
                        integer(value,"minimum_cycles",1), optionalString(value,"reward_profile",""),
                        longMap(value,"progression"), string(value,"message_key")
                ));
            }
            current.set(new Snapshot(generation.incrementAndGet(),definitions));
            return new ReloadResult(true,"");
        } catch (Exception exception) { return new ReloadResult(false, exception.getClass().getSimpleName()+": "+exception.getMessage()); }
    }

    private static Map<String,Object> map(Map<String,Object> source,String key){return mapValue(source.get(key),key);}    
    private static Map<String,Object> mapValue(Object value,String label){if(!(value instanceof Map<?,?> raw))throw new IllegalArgumentException("Expected map at "+label);Map<String,Object> out=new LinkedHashMap<>();raw.forEach((k,v)->out.put(String.valueOf(k),v));return out;}
    private static String string(Map<String,Object> source,String key){Object value=source.get(key);if(value==null)throw new IllegalArgumentException("Missing key: "+key);return String.valueOf(value);}    
    private static String optionalString(Map<String,Object> source,String key,String fallback){Object value=source.get(key);return value==null?fallback:String.valueOf(value);}    
    private static int integer(Map<String,Object> source,String key,int fallback){Object value=source.get(key);return value==null?fallback:value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}
    private static Map<String,Long> longMap(Map<String,Object> source,String key){Object value=source.get(key);if(value==null)return Map.of();Map<String,Object> raw=mapValue(value,key);Map<String,Long> out=new LinkedHashMap<>();raw.forEach((k,v)->out.put(k,v instanceof Number n?n.longValue():Long.parseLong(String.valueOf(v))));return out;}
    public record Snapshot(long generation,Map<String,AnniversaryDefinition> definitions){public Snapshot{definitions=Map.copyOf(definitions);}}
    public record ReloadResult(boolean success,String detail){}
}
