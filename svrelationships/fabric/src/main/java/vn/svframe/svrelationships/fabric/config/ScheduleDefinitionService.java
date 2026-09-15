package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.gameplay.ScheduleDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ScheduleDefinitionService {
    private final Path root; private final AtomicLong generation = new AtomicLong(); private final AtomicReference<Snapshot> current = new AtomicReference<>();
    public ScheduleDefinitionService(Path root) { this.root = Objects.requireNonNull(root, "root"); }
    public void initialize() {
        try { Path target=root.resolve("schedules.yml"); if(!Files.exists(target)){Files.createDirectories(target.getParent());try(InputStream input=ScheduleDefinitionService.class.getResourceAsStream("/defaults/schedules.yml")){if(input==null)throw new IOException("Missing bundled resource: /defaults/schedules.yml");Files.copy(input,target,StandardCopyOption.COPY_ATTRIBUTES);}} }
        catch(IOException exception){throw new IllegalStateException("Unable to install schedule definitions",exception);} ReloadResult result=reload();if(!result.success())throw new IllegalStateException("Unable to load schedule definitions: "+result.detail());
    }
    public Snapshot snapshot(){return Objects.requireNonNull(current.get(),"schedules not initialized");}
    public ReloadResult reload(){
        try{Map<String,Object> rootMap;try(InputStream input=Files.newInputStream(root.resolve("schedules.yml"))){rootMap=mapValue(new Yaml(new SafeConstructor(new LoaderOptions())).load(input),"schedules.yml");}
            String defaultProfile=string(rootMap,"default_profile"); Map<String,String> personalityProfiles=stringMap(rootMap,"personality_profiles"); Map<String,ScheduleDefinition> profiles=new LinkedHashMap<>();
            for(var entry:map(rootMap,"profiles").entrySet()){Map<String,Object> value=mapValue(entry.getValue(),"schedule "+entry.getKey());List<ScheduleDefinition.Entry> entries=new ArrayList<>();Object raw=value.get("entries");if(raw instanceof List<?> list)for(Object item:list){Map<String,Object> e=mapValue(item,"schedule entry");entries.add(new ScheduleDefinition.Entry(string(e,"id"),integer(e,"start_tick"),integer(e,"end_tick"),string(e,"activity"),bool(e,"materialize",true),string(e,"message_key")));}profiles.put(entry.getKey(),new ScheduleDefinition(entry.getKey(),entries));}
            if(!profiles.containsKey(defaultProfile))throw new IllegalArgumentException("Unknown default schedule profile: "+defaultProfile);for(String profile:personalityProfiles.values())if(!profiles.containsKey(profile))throw new IllegalArgumentException("Unknown personality schedule profile: "+profile);
            current.set(new Snapshot(generation.incrementAndGet(),defaultProfile,personalityProfiles,profiles));return new ReloadResult(true,"");
        }catch(Exception exception){return new ReloadResult(false,exception.getClass().getSimpleName()+": "+exception.getMessage());}
    }
    private static Map<String,Object> map(Map<String,Object>s,String k){return mapValue(s.get(k),k);}private static Map<String,Object> mapValue(Object v,String l){if(!(v instanceof Map<?,?> r))throw new IllegalArgumentException("Expected map at "+l);Map<String,Object> o=new LinkedHashMap<>();r.forEach((k,x)->o.put(String.valueOf(k),x));return o;}private static String string(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)throw new IllegalArgumentException("Missing key: "+k);return String.valueOf(v);}private static int integer(Map<String,Object>s,String k){Object v=s.get(k);return v instanceof Number n?n.intValue():Integer.parseInt(string(s,k));}private static boolean bool(Map<String,Object>s,String k,boolean f){Object v=s.get(k);return v==null?f:v instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(v));}private static Map<String,String> stringMap(Map<String,Object>s,String k){Object v=s.get(k);if(v==null)return Map.of();Map<String,Object> r=mapValue(v,k);Map<String,String> o=new LinkedHashMap<>();r.forEach((a,b)->o.put(a,String.valueOf(b)));return o;}
    public record Snapshot(long generation,String defaultProfile,Map<String,String> personalityProfiles,Map<String,ScheduleDefinition> profiles){public Snapshot{personalityProfiles=Map.copyOf(personalityProfiles);profiles=Map.copyOf(profiles);}}public record ReloadResult(boolean success,String detail){}
}
