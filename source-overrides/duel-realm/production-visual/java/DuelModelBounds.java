package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/** Measures vertices from the effective Cobblemon poser, including addon providers. */
final class DuelModelBounds {
    record Bounds(double height, double span, double minY, double centerX, double centerZ) {}
    static Bounds measure(PokemonEntity entity) {
        var state = (PokemonClientDelegate)entity.getDelegate();
        var model = VaryingModelRepository.INSTANCE.getPoser(entity.getPokemon().getSpecies().getResourceIdentifier(), state);
        var resolvedTexture = VaryingModelRepository.INSTANCE.getTextureNoSubstitute(entity.getPokemon().getSpecies().getResourceIdentifier(),state);
        if (resolvedTexture == null) throw new IllegalStateException("No actual model texture for " + entity.getPokemon().getSpecies().getResourceIdentifier());
        // Match PokemonRenderer's normal wrapper/context initialization before animation or bone traversal.
        var entityModel = new com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PosablePokemonEntityModel();
        entityModel.setPosableModel(model);
        model.setContext(entityModel.getContext());
        entityModel.setupEntityTypeContext(entity);
        model.setDefault();
        model.applyAnimations(entity, state, 0, 0, 0, 0, 0);
        MatrixStack matrices = new MatrixStack();
        matrices.scale(-1, -1, 1);
        float naturalScale = entity.getPokemon().getForm().getBaseScale() * entity.getPokemon().getEffectiveScale();
        matrices.scale(naturalScale, naturalScale, naturalScale);
        // Same model-space anchor used by Minecraft's LivingEntityRenderer.
        matrices.translate(0, -1.501, 0);
        Measure vertices = new Measure();
        model.getRootPart().render(model.getContext(), matrices, vertices, 0xF000F0, 0, -1);
        model.setDefault();
        if (vertices.count == 0 || !Double.isFinite(vertices.minY))
            throw new IllegalStateException("Resolved Pokémon model emitted no measurable vertices: " + entity.getPokemon().getSpecies().getResourceIdentifier());
        return new Bounds(Math.max(.1, vertices.maxY - vertices.minY),
            Math.max(vertices.maxX - vertices.minX, vertices.maxZ - vertices.minZ), vertices.minY,
            (vertices.minX + vertices.maxX)/2, (vertices.minZ + vertices.maxZ)/2);
    }
    private static final class Measure implements VertexConsumer {
        int count;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        public VertexConsumer vertex(float x,float y,float z) {
            count++; minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);return this;
        }
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer texture(float u,float v){return this;}
        public VertexConsumer overlay(int u,int v){return this;}
        public VertexConsumer light(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
    }
}
