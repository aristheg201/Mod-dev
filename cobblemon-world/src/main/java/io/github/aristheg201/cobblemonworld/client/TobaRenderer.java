package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.boss.TobaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class TobaRenderer extends MobRenderer<TobaEntity, TobaModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CobblemonWorldMod.MOD_ID, "textures/entity/boss/toba.png");

    public TobaRenderer(EntityRendererProvider.Context context) {
        super(context, new TobaModel(context.bakeLayer(TobaModel.LAYER)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(TobaEntity entity) {
        return TEXTURE;
    }
}
