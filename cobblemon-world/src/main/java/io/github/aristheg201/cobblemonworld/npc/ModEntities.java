package io.github.aristheg201.cobblemonworld.npc;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    private static final ResourceLocation NPC_ID =
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "npc");
    private static final ResourceKey<EntityType<?>> NPC_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, NPC_ID);

    public static final EntityType<CWorldNpcEntity> NPC = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            NPC_KEY,
            EntityType.Builder.<CWorldNpcEntity>of(CWorldNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(NPC_KEY)
    );

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(NPC, CWorldNpcEntity.createAttributes());
    }
}
