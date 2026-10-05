package vn.worldcomesalive.client;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;

/** Original articulated humanoid meshes, using resource-pack replaceable 64x64 skin UVs. */
public final class CitizenModels {
    public static TexturedModelData mesh(boolean female){
        ModelData data=BipedEntityModel.getModelData(Dilation.NONE,0);
        ModelPartData root=data.getRoot();
        root.addChild("head",ModelPartBuilder.create().uv(0,0).cuboid(-4,-8,-4,8,8,8),ModelTransform.NONE);
        // Sculpted hair: short swept sides on the male model; shoulder-length hair on the female model.
        ModelPartBuilder hair=ModelPartBuilder.create().uv(32,0).cuboid(-4.2f,-8.25f,-4.15f,8.4f,2.6f,8.3f);
        if(female)hair.uv(32,4).cuboid(-4.25f,-5.7f,1.8f,8.5f,7.4f,2.5f).cuboid(-4.25f,-5.7f,-.5f,1.15f,5.5f,3.8f).cuboid(3.1f,-5.7f,-.5f,1.15f,5.5f,3.8f);
        else hair.uv(32,3).cuboid(-4.15f,-5.8f,1.8f,8.3f,3.2f,2.3f);
        root.addChild("hat",hair,ModelTransform.NONE);
        ModelPartBuilder torso=ModelPartBuilder.create().uv(16,16).cuboid(female?-3.5f:-4,0,-2,female?7:8,12,4);
        torso.uv(16,32).cuboid(female?-3.7f:-4.2f,8,-2.15f,female?7.4f:8.4f,1.5f,4.3f);
        if(female)torso.uv(16,36).cuboid(-4.1f,9.5f,-2.25f,8.2f,3.2f,4.5f);
        root.addChild("body",torso,ModelTransform.NONE);
        float arm=female?3:4;
        root.addChild("right_arm",ModelPartBuilder.create().uv(40,16).cuboid(female?-2:-3,-2,-2,arm,12,4),ModelTransform.pivot(-5,2,0));
        root.addChild("left_arm",ModelPartBuilder.create().uv(32,48).cuboid(-1,-2,-2,arm,12,4),ModelTransform.pivot(5,2,0));
        root.addChild("right_leg",ModelPartBuilder.create().uv(0,16).cuboid(-2,0,-2,4,12,4).uv(0,32).cuboid(-2.1f,8,-2.25f,4.2f,4,4.5f),ModelTransform.pivot(-1.9f,12,0));
        root.addChild("left_leg",ModelPartBuilder.create().uv(16,48).cuboid(-2,0,-2,4,12,4).uv(0,32).cuboid(-2.1f,8,-2.25f,4.2f,4,4.5f),ModelTransform.pivot(1.9f,12,0));
        return TexturedModelData.of(data,64,64);
    }
    private CitizenModels(){}
}
