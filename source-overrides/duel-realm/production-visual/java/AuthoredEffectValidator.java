package vn.svarcade.tcg.data;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Startup validation identifies the card and field, rather than accepting broken authored rules. */
public final class AuthoredEffectValidator {
    private static JsonObject resource(String path){try(var in=AuthoredEffectValidator.class.getResourceAsStream(path)){
        return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(IOException e){throw new IllegalStateException(path,e);}}
    private static final JsonObject EN=resource("/assets/svarcade_tcg/lang/en_us.json"),VI=resource("/assets/svarcade_tcg/lang/vi_vn.json"),PROFILES=resource("/data/svarcade_tcg/duel_vfx_profiles.json");
    private static void key(String id,String field,String key){if(key!=null&&(!EN.has(key)||!VI.has(key)))throw new IllegalArgumentException(id+": "+field+": missing EN/VI key "+key);}
    private static void presentation(String id,EffectSpec.Presentation v){
        if(v==null)return;
        if(v.profile()!=null&&!PROFILES.has(v.profile().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(id+": vfx.profile: "+v.profile());
        for(var stage:EffectSpec.list(v.stages()))if(!PROFILES.has(EffectSpec.value(stage.profile(),EffectSpec.value(v.profile(),"normal")).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException(id+": vfx.stages.profile: "+stage.profile());
    }
    private static void spec(String id,EffectSpec spec){if(spec==null)return;
        try{spec.validate();}catch(RuntimeException e){throw new IllegalArgumentException(id+": effect: "+e.getMessage(),e);}
        key(id,"effect.textKey",spec.textKey());presentation(id,spec.vfx());
        for(var stage:EffectSpec.list(spec.stages()))spec(id+"."+stage.id(),stage.effect().spec());
    }
    public static void validate(Catalog catalog){
        for(var card:catalog.cards().values())if(card.effect()!=null)spec(card.id(),card.effect().spec());
        for(var d:SpecialAspectCards.definitions()){
            key(d.id(),"displayNameKey",d.displayNameKey());key(d.id(),"effect.textKey",d.effect().spec().textKey());
            if(d.requiredAspects().isEmpty()||d.requiresMods().stream().anyMatch(m->!m.matches("[a-z0-9_-]+")))throw new IllegalArgumentException(d.id()+": requires: invalid provider requirement");
            spec(d.id(),d.effect().spec());
        }
    }
    private AuthoredEffectValidator(){}
}
