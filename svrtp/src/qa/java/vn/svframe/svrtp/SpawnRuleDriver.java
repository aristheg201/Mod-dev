package vn.svframe.svrtp;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnRules;
import com.cobblemon.mod.common.api.spawning.rules.SpawnRule;
import com.cobblemon.mod.common.api.spawning.rules.component.FilterRuleComponent;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Actual Cobblemon rule parser and MoLang engine, isolated from production. */
final class SpawnRuleDriver {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try {
                var gson=CobblemonSpawnRules.INSTANCE.getGson();
                String oldJson=Files.readString(Path.of("original-rule.json")),newJson=Files.readString(Path.of("optimized-rule.json"));
                var original=gson.fromJson(oldJson,SpawnRule.class);var optimized=gson.fromJson(newJson,SpawnRule.class);
                var filter=(FilterRuleComponent)optimized.getComponents().getFirst();
                var nativeRule=CobblemonSpawnRules.INSTANCE.getRules().get(net.minecraft.resources.ResourceLocation.parse("cobblemon:svf_fakemon_only_overworld"));
                if(nativeRule==null || nativeRule.getComponents().size()!=1)throw new IllegalStateException("Optimized datapack did not load through the native registry");
                var names=new ArrayList<String>();var matcher=Pattern.compile("species == '([^']+)'").matcher(oldJson);while(matcher.find())names.add(matcher.group(1));
                if(new HashSet<>(names).size()!=1025)throw new IllegalStateException("Missing original species");
                long oldNanos=0,newNanos=0;
                var cases=new ArrayList<>(names);cases.addAll(List.of("fakechu","chu","pika","bulbasaur_custom","custom:bulbasaur","fake-pecharunt"));
                for(String name:cases){
                    var detail=new PokemonSpawnDetail();detail.setPokemon(PokemonProperties.Companion.parse(name));detail.autoLabel();
                    long start=System.nanoTime();boolean previouslyBlocked=false;
                    for(var c:original.getComponents())if(((FilterRuleComponent)c).getSpawnDetailSelector().selects(detail)){previouslyBlocked=true;break;}
                    oldNanos+=System.nanoTime()-start;start=System.nanoTime();
                    // Match FilterRuleComponent's actual environment binding, rather than
                    // MoLangRuntime's separate query context parameter.
                    filter.getRuntime().getEnvironment().setSimpleVariable("spawn_detail",detail.getStruct());
                    boolean nowAllowed=MoLangExtensionsKt.resolveBoolean(filter.getRuntime(),filter.getAllow(),Map.of());
                    newNanos+=System.nanoTime()-start;
                    if(nowAllowed==previouslyBlocked)throw new IllegalStateException("Changed blacklist behavior: "+name+" oldBlocked="+previouslyBlocked+" newAllowed="+nowAllowed);
                }
                System.out.println("SPAWN_RULE_QA_PASS nativeRegistry=true nativeMolang=true exactOfficialSpecies=1025 nonMembers=6 oldFilters=65 newFilters=1 originalEvalMs="+oldNanos/1000000.0+" optimizedEvalMs="+newNanos/1000000.0);
            }catch(Exception e){System.out.println("SPAWN_RULE_QA_FAIL "+e);e.printStackTrace();}
            server.halt(false);
        });
    }
}
