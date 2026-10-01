package vn.svarcade.tcg.client.render;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

record DuelVfxProfile(String particle,String fallback,String emitter,String shape,int color,
                      int maxParticles,int maxLifetime,int spawnRate,int maxTrailSegments,
                      int maxConcurrentInstances,double distanceCull,String sound) {
    private static final Map<String,DuelVfxProfile> PROFILES=load();
    private static Map<String,DuelVfxProfile> load() {
        try(var in=DuelVfxProfile.class.getResourceAsStream("/data/svarcade_tcg/duel_vfx_profiles.json")) {
            return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),new TypeToken<Map<String,DuelVfxProfile>>(){}.getType());
        }catch(Exception e){throw new IllegalStateException("Missing duel VFX profiles",e);}
    }
    static String normalizeKey(String element) {
        String key=element==null?"normal":element.trim().toLowerCase(java.util.Locale.ROOT);
        int colon=key.lastIndexOf(':');
        if(colon>=0&&colon+1<key.length())key=key.substring(colon+1);
        return PROFILES.containsKey(key)?key:"normal";
    }
    static DuelVfxProfile resolve(String element) {return PROFILES.get(normalizeKey(element));}
}
