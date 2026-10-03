package vn.svarcade.tcg.physical;

import com.google.gson.Gson;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.*;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.*;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.provider.number.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import vn.svarcade.tcg.data.Catalog;
import java.nio.file.*;
import java.util.*;

/** Chest injection is configurable; each function application uses the live hydrated catalog and a new token. */
public final class PhysicalLoot {
    public record Chest(float physicalChance,float blankChance,int minBlank,int maxBlank){}
    public record Config(Map<String,Chest> tables,Map<String,Integer> rarityWeights){}
    private static final Gson JSON=new Gson();
    private static Config config;
    public static final LootFunctionType<RandomCard> RANDOM_CARD=Registry.register(Registries.LOOT_FUNCTION_TYPE,Identifier.of("svarcade_tcg","random_physical_card"),new LootFunctionType<>(RandomCard.CODEC));
    public static void initialize(){reload();LootTableEvents.MODIFY.register((key,builder,source,registries)->{
        Chest chest=config.tables().get(key.getValue().toString());if(chest==null||!source.isBuiltin())return;
        builder.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
            .conditionally(RandomChanceLootCondition.builder(chest.physicalChance()))
            .with(ItemEntry.builder(CardItems.PHYSICAL_CARD).apply(()->new RandomCard(List.of()))));
        builder.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
            .conditionally(RandomChanceLootCondition.builder(chest.blankChance()))
            .with(ItemEntry.builder(CardItems.BLANK_CARD).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(chest.minBlank(),chest.maxBlank())))));
    });}
    public static void reload(){
        try {
            Path p=FabricLoader.getInstance().getConfigDir().resolve("svarcade-tcg/physical-loot.json");Files.createDirectories(p.getParent());
            if(!Files.exists(p))try(var in=PhysicalLoot.class.getResourceAsStream("/data/svarcade_tcg/physical_loot.json")){Files.copy(Objects.requireNonNull(in),p);}
            Config next=JSON.fromJson(Files.readString(p),Config.class);Objects.requireNonNull(next.tables());Objects.requireNonNull(next.rarityWeights());
            next.tables().forEach((id,c)->{Identifier.of(id);if(!Float.isFinite(c.physicalChance())||!Float.isFinite(c.blankChance())||c.physicalChance()<0||c.physicalChance()>1||c.blankChance()<0||c.blankChance()>1||c.minBlank()<1||c.maxBlank()>64||c.maxBlank()<c.minBlank())throw new IllegalArgumentException("Invalid chest loot configuration: "+id);});
            if(next.rarityWeights().values().stream().anyMatch(w->w<0||w>1_000_000)||next.rarityWeights().values().stream().noneMatch(w->w>0))throw new IllegalArgumentException("Invalid rarity weights");
            config=next;
        }catch(Exception e){throw new IllegalStateException("Cannot load physical loot configuration",e);}
    }
    public static final class RandomCard extends ConditionalLootFunction {
        public static final MapCodec<RandomCard> CODEC=RecordCodecBuilder.mapCodec(i->addConditionsField(i).apply(i,RandomCard::new));
        public RandomCard(List<LootCondition> conditions){super(conditions);}
        @Override public LootFunctionType<RandomCard> getType(){return RANDOM_CARD;}
        @Override protected ItemStack process(ItemStack stack,LootContext context){
            Catalog catalog=PhysicalCards.catalog();if(catalog==null)return ItemStack.EMPTY;
            Map<String,List<Catalog.Card>> byRarity=new TreeMap<>();
            catalog.cards().values().stream().sorted(Comparator.comparing(Catalog.Card::id))
                .filter(c->config.rarityWeights().getOrDefault(c.rarity(),0)>0)
                .forEach(c->byRarity.computeIfAbsent(c.rarity(),ignored->new ArrayList<>()).add(c));
            long total=byRarity.keySet().stream().mapToLong(rarity->config.rarityWeights().get(rarity)).sum();if(total<=0)return ItemStack.EMPTY;
            long roll=(long)(context.getRandom().nextDouble()*total);List<Catalog.Card> pool=List.of();
            for(var rarity:byRarity.entrySet()){roll-=config.rarityWeights().get(rarity.getKey());if(roll<0){pool=rarity.getValue();break;}}
            Catalog.Card chosen=pool.get(context.getRandom().nextInt(pool.size()));
            return CardItems.physical(PhysicalCardData.create(catalog,chosen.id(),"Normal","CHEST_LOOT","",""));
        }
    }
    private PhysicalLoot(){}
}
