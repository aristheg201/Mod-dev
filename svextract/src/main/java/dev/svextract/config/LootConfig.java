package dev.svextract.config;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Immutable weighted loot definitions; actual rolls are scoped to one raid and one cache identity. */
public record LootConfig(List<Cache> caches) {
    public record Cache(String id,int x,int y,int z,String item,int count,List<WeightedLoot.Entry> choices) {
        // Legacy six-field loot.json entries remain valid without destructive config migration.
        public Cache(String id,int x,int y,int z,String item,int count) { this(id,x,y,z,item,count,null); }
        public Cache {
            if (choices == null || choices.isEmpty()) {
                choices=List.of(new WeightedLoot.Entry(item,count,1));
            } else choices=List.copyOf(choices);
        }
    }
    public LootConfig { caches=List.copyOf(caches); }
    private static Cache cache(String id,int x,int z,String item,int count,WeightedLoot.Entry... alternatives){
        List<WeightedLoot.Entry> variants=new ArrayList<>();
        variants.add(new WeightedLoot.Entry(item,count,6));
        variants.addAll(Arrays.asList(alternatives));
        return new Cache(id,x,65,z,item,count,variants);
    }
    public static LootConfig defaults(){return new LootConfig(List.of(
        cache("platform_ammunition",-4,-4,"pointblank:ammo9mm",8,
            new WeightedLoot.Entry("minecraft:iron_ingot",5,3),new WeightedLoot.Entry("minecraft:gold_ingot",2,1)),
        cache("workshop_salvage",4,-4,"minecraft:copper_ingot",4,
            new WeightedLoot.Entry("minecraft:redstone",9,3),new WeightedLoot.Entry("minecraft:iron_ingot",5,2)),
        cache("abandoned_sidearm",32,-4,"pointblank:glock17",1,
            new WeightedLoot.Entry("pointblank:ammo9mm",12,5),new WeightedLoot.Entry("minecraft:gold_ingot",4,2)),
        cache("platform_locker",-32,-4,"minecraft:iron_ingot",7,
            new WeightedLoot.Entry("minecraft:emerald",2,2),new WeightedLoot.Entry("minecraft:compass",1,1)),
        cache("signal_substation",-20,16,"minecraft:redstone",12,
            new WeightedLoot.Entry("minecraft:amethyst_shard",4,2),new WeightedLoot.Entry("minecraft:diamond",1,1)),
        cache("drainage_supplies",-8,22,"minecraft:copper_ingot",6,
            new WeightedLoot.Entry("minecraft:ender_pearl",1,2),new WeightedLoot.Entry("minecraft:gold_ingot",2,2)),
        cache("research_cabinet",18,22,"minecraft:amethyst_shard",8,
            new WeightedLoot.Entry("minecraft:echo_shard",1,1),new WeightedLoot.Entry("minecraft:diamond",2,1)),
        cache("foundry_safe",30,-20,"minecraft:gold_ingot",5,
            new WeightedLoot.Entry("minecraft:netherite_scrap",1,1),new WeightedLoot.Entry("minecraft:emerald",4,2)),
        cache("service_cache",18,-16,"pointblank:ammo9mm",16,
            new WeightedLoot.Entry("minecraft:golden_apple",1,1),new WeightedLoot.Entry("minecraft:iron_ingot",8,2)),
        cache("maintenance_case",-26,-18,"minecraft:iron_ingot",9,
            new WeightedLoot.Entry("pointblank:glock17",1,1),new WeightedLoot.Entry("minecraft:lapis_lazuli",8,2)),
        cache("clinic_medication",-33,17,"minecraft:golden_apple",1,
            new WeightedLoot.Entry("minecraft:honey_bottle",3,5),new WeightedLoot.Entry("minecraft:glistering_melon_slice",6,4)),
        cache("quarantine_records",-30,3,"minecraft:paper",12,
            new WeightedLoot.Entry("minecraft:emerald",4,2),new WeightedLoot.Entry("minecraft:compass",1,3)),
        cache("radio_relay_components",-29,-25,"minecraft:redstone",16,
            new WeightedLoot.Entry("minecraft:quartz",8,3),new WeightedLoot.Entry("minecraft:diamond",2,1)),
        cache("checkpoint_lockbox",23,-24,"minecraft:iron_ingot",9,
            new WeightedLoot.Entry("pointblank:ammo9mm",24,5),new WeightedLoot.Entry("minecraft:emerald",6,2)),
        cache("security_dispatch",30,-24,"minecraft:gold_ingot",4,
            new WeightedLoot.Entry("minecraft:ender_eye",1,1),new WeightedLoot.Entry("minecraft:crossbow",1,2)),
        cache("research_evidence",29,23,"minecraft:amethyst_shard",10,
            new WeightedLoot.Entry("minecraft:echo_shard",2,1),new WeightedLoot.Entry("minecraft:diamond",2,1)),
        cache("freight_salvage",-10,24,"minecraft:copper_ingot",12,
            new WeightedLoot.Entry("minecraft:iron_ingot",8,5),new WeightedLoot.Entry("minecraft:gold_ingot",3,2)),
        cache("freight_manifest",-17,24,"minecraft:compass",1,
            new WeightedLoot.Entry("minecraft:emerald",4,2),new WeightedLoot.Entry("minecraft:ender_pearl",2,2)),
        cache("platform_maintenance",-3,16,"minecraft:iron_ingot",12,
            new WeightedLoot.Entry("minecraft:lapis_lazuli",8,2),new WeightedLoot.Entry("minecraft:diamond",1,1)),
        cache("operations_fieldkit",10,3,"minecraft:golden_apple",2,
            new WeightedLoot.Entry("pointblank:ammo9mm",20,5),new WeightedLoot.Entry("minecraft:totem_of_undying",1,1))));
    }
    /**
     * Resolve optional-mod loot once at server initialization, never during a raid.
     * Missing optional items are omitted; unrelated missing items still fail validation.
     */
    public LootConfig filterAvailable(java.util.function.Predicate<String> registered,
                                      java.util.function.Predicate<String> optionalUnavailable) {
        Objects.requireNonNull(registered);
        Objects.requireNonNull(optionalUnavailable);
        List<Cache> compatible=new ArrayList<>();
        for (Cache cache:caches) {
            List<WeightedLoot.Entry> choices=new ArrayList<>();
            for (WeightedLoot.Entry entry:cache.choices()) {
                if (registered.test(entry.item())) choices.add(entry);
                else if (!optionalUnavailable.test(entry.item()))
                    throw new IllegalArgumentException("loot.json cache "+cache.id()+": unknown item "+entry.item());
            }
            if (choices.isEmpty()) {
                if (!registered.test("minecraft:iron_ingot"))
                    throw new IllegalArgumentException("No safe fallback for optional loot cache "+cache.id());
                choices.add(new WeightedLoot.Entry("minecraft:iron_ingot",1,1));
            }
            WeightedLoot.Entry primary=choices.getFirst();
            compatible.add(new Cache(cache.id(),cache.x(),cache.y(),cache.z(),
                    primary.item(),primary.count(),choices));
        }
        return new LootConfig(compatible);
    }

    public WeightedLoot.Entry roll(Cache cache,UUID raid){return WeightedLoot.choose(raid,cache.id(),cache.choices());}
    public static LootConfig load(Path path) throws IOException {
        var json=new GsonBuilder().setPrettyPrinting().create();
        if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,json.toJson(defaults())+"\n");}
        try{
            if(Files.size(path)>65536)throw new IllegalArgumentException("file exceeds 64 KiB");
            LootConfig table=json.fromJson(Files.readString(path),LootConfig.class);
            if(table==null||table.caches().isEmpty()||table.caches().size()>32)throw new IllegalArgumentException("caches must contain 1-32 entries");
            Set<String> ids=new HashSet<>(),positions=new HashSet<>();
            for(Cache c:table.caches()){
                if(c.id()==null||!c.id().matches("[a-z0-9_]{1,48}")||!ids.add(c.id()))throw new IllegalArgumentException("cache id is invalid or duplicated");
                if(Math.abs((long)c.x())>35||Math.abs((long)c.z())>27||c.y()<64||c.y()>74||!positions.add(c.x()+":"+c.y()+":"+c.z()))throw new IllegalArgumentException("cache "+c.id()+" position is outside both prepared maps or duplicated");
                if(c.choices().size()>12)throw new IllegalArgumentException("cache "+c.id()+" has more than 12 weighted choices");
                for(WeightedLoot.Entry choice:c.choices())new WeightedLoot.Entry(choice.item(),choice.count(),choice.weight());
            }
            return table;
        }catch(RuntimeException e){throw new IOException(path+": "+e.getMessage(),e);}
    }
}
