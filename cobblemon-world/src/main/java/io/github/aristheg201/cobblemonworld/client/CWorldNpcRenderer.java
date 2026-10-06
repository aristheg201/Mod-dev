package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.npc.CWorldNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class CWorldNpcRenderer extends MobRenderer<CWorldNpcEntity, PlayerModel<CWorldNpcEntity>> {
    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CobblemonWorldMod.MOD_ID, "textures/entity/npc/default.png");

    public CWorldNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CWorldNpcEntity entity) {
        return DEFAULT_TEXTURE;
    }
}
