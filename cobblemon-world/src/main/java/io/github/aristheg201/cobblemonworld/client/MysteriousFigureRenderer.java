package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.boss.MysteriousFigureEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class MysteriousFigureRenderer extends MobRenderer<MysteriousFigureEntity, MysteriousFigureModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CobblemonWorldMod.MOD_ID, "textures/entity/boss/mysterious_figure.png");

    public MysteriousFigureRenderer(EntityRendererProvider.Context context) {
        super(context, new MysteriousFigureModel(context.bakeLayer(MysteriousFigureModel.LAYER)), 0.55F);
    }

    @Override
    public ResourceLocation getTextureLocation(MysteriousFigureEntity entity) {
        return TEXTURE;
    }
}
