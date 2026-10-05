package vn.worldcomesalive.client;
import vn.worldcomesalive.world.CitizenEntity;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Rendering never consults private NPC state; two tracked presentation fields suffice. */
public final class CitizenRenderer extends MobEntityRenderer<CitizenEntity,BipedEntityModel<CitizenEntity>> {
    public static final EntityModelLayer MALE=new EntityModelLayer(Identifier.of("worldcomesalive","citizen_male"),"main"),FEMALE=new EntityModelLayer(Identifier.of("worldcomesalive","citizen_female"),"main");
    private final BipedEntityModel<CitizenEntity> male,female;
    public CitizenRenderer(EntityRendererFactory.Context context){super(context,new BipedEntityModel<>(context.getPart(MALE)),.4f);male=model;female=new BipedEntityModel<>(context.getPart(FEMALE));addFeature(new net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer<>(this,context.getHeldItemRenderer()));}
    @Override protected boolean hasLabel(CitizenEntity citizen){return net.minecraft.client.MinecraftClient.getInstance().targetedEntity==citizen&&citizen.squaredDistanceTo(net.minecraft.client.MinecraftClient.getInstance().player)<64;}
    @Override public Identifier getTexture(CitizenEntity citizen){String[] parts=citizen.presentation().split(":");String gender=parts[0].equals("female")?"female":"male";int skin=parts.length>1?Integer.parseInt(parts[1]):0;Identifier custom=Identifier.of("worldcomesalive","textures/entity/citizen/"+gender+"_"+Math.floorMod(skin,4)+".png");return net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(custom).isPresent()?custom:Identifier.ofVanilla("textures/entity/player/"+(gender.equals("female")?"slim/alex.png":"wide/steve.png"));}
    @Override public void render(CitizenEntity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light){model=entity.presentation().startsWith("female")?female:male;model.child=entity.isBaby();super.render(entity,yaw,delta,matrices,buffers,light);}
}
