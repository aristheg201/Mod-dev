package io.github.aristheg201.cobblemonworld.boss;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModBossEntities {
    private ModBossEntities() {}

    public static final EntityType<MysteriousFigureEntity> MYSTERIOUS_FIGURE = register(
            "mysterious_figure",
            EntityType.Builder.<MysteriousFigureEntity>of(MysteriousFigureEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .eyeHeight(1.68F)
                    .clientTrackingRange(12)
    );

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String id, EntityType.Builder<T> builder) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, id);
        EntityType<T> entityType = builder.build(location.toString());
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, location, entityType);
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(MYSTERIOUS_FIGURE, MysteriousFigureEntity.createAttributes());
    }
}
