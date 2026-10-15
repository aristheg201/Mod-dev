package dev.svextract.match;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Validated rule snapshots; disabled engines cannot be enabled by editing a menu flag. */
public final class GameModes {
    public record Definition(String id,String map,boolean enabled,int timeoutSeconds,int friendlyBots,
        int hostileBots,boolean equipmentRisk,boolean optionalObjectives,int scoreLimit,int respawnSeconds,
        int reconnectSeconds,int checkpointTicks,int botReloadSeconds){}
    private final Map<String,Definition> definitions;
    private GameModes(List<Definition> entries){
        if(entries.size()!=6)throw new IllegalArgumentException("modes.json requires all six distinct modes");
        Map<String,Definition> validated=new LinkedHashMap<>();
        for(Definition d:entries){
            if(d==null||!Set.of("training","operations","black_zone","battle_royale","versus","apex").contains(d.id())||validated.putIfAbsent(d.id(),d)!=null)
                throw new IllegalArgumentException("Unknown or duplicate game mode");
            if(d.map()==null||!d.map().matches("[a-z0-9_]{1,32}")||d.timeoutSeconds()<60||d.timeoutSeconds()>7200
                ||d.friendlyBots()<0||d.friendlyBots()>4||d.hostileBots()<0||d.hostileBots()>32
                ||d.scoreLimit()<1||d.scoreLimit()>500||d.respawnSeconds()<1||d.respawnSeconds()>60
                ||d.reconnectSeconds()<5||d.reconnectSeconds()>300||d.checkpointTicks()<20||d.checkpointTicks()>1200
                ||d.botReloadSeconds()<1||d.botReloadSeconds()>30)
                throw new IllegalArgumentException("Invalid bounded settings for mode "+d.id());
            if(d.enabled()&&!supported(d.id()))throw new IllegalArgumentException(d.id()+": match engine is not implemented; cannot enable it");
            if(d.enabled()&&!Set.of("redline_station","blackglass_industrial").contains(d.map()))throw new IllegalArgumentException(d.id()+": map is not prepared by an implemented template: "+d.map());
            if(d.id().equals("versus")&&(d.friendlyBots()!=4||d.hostileBots()!=5||d.equipmentRisk()))
                throw new IllegalArgumentException("Private Versus requires one human, four allied bots, five opposing bots and no equipment risk");
        }
        definitions=Collections.unmodifiableMap(validated);
    }
    public static boolean supported(String id){return Set.of("training","operations","versus").contains(id);}
    public List<Definition> all(){return List.copyOf(definitions.values());}
    public Definition get(String id){Definition d=definitions.get(id);if(d==null||!d.enabled()||!supported(id))throw new IllegalArgumentException("Mode is unavailable: "+id);return d;}
    public static GameModes load(Path file)throws IOException{
        Gson gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        if(!Files.exists(file)){
            Files.createDirectories(file.getParent());
            try(var input=GameModes.class.getResourceAsStream("/data/svextract/modes.json")){
                if(input==null)throw new IOException("Missing bundled mode configuration");Files.copy(input,file);
            }
        }
        if(Files.size(file)>32768)throw new IOException(file+": mode configuration exceeds 32 KiB");
        try{return new GameModes(Arrays.asList(gson.fromJson(Files.readString(file),Definition[].class)));}
        catch(RuntimeException e){throw new IOException(file+": "+e.getMessage(),e);}
    }
}
