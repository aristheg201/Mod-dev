package io.github.aristheg201.cobblemonworld.client;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.boss.TobaEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class TobaModel extends HierarchicalModel<TobaEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CobblemonWorldMod.MOD_ID, "toba"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart tendril1;
    private final ModelPart tendril2;
    private final ModelPart tendril3;
    private final ModelPart tendril4;
    private final ModelPart core;

    public TobaModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
        this.tendril1 = root.getChild("tendril_1");
        this.tendril2 = root.getChild("tendril_2");
        this.tendril3 = root.getChild("tendril_3");
        this.tendril4 = root.getChild("tendril_4");
        this.core = root.getChild("core");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 32).addBox(-7.0F, -20.0F, -4.0F, 14.0F, 20.0F, 8.0F),
                PartPose.offset(0.0F, -18.0F, 0.0F));
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 12.0F, 10.0F),
                PartPose.offset(0.0F, -38.0F, 0.0F));
        root.addOrReplaceChild("jaw",
                CubeListBuilder.create().texOffs(42, 0).addBox(-4.0F, 0.0F, -5.5F, 8.0F, 4.0F, 9.0F),
                PartPose.offset(0.0F, -39.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(48, 26).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 27.0F, 6.0F)
                        .texOffs(72, 26).addBox(-4.0F, 25.0F, -4.0F, 8.0F, 6.0F, 8.0F),
                PartPose.offset(9.0F, -35.0F, 0.0F));
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(48, 26).mirror().addBox(-2.5F, 0.0F, -3.0F, 5.0F, 27.0F, 6.0F)
                        .texOffs(72, 26).addBox(-4.0F, 25.0F, -4.0F, 8.0F, 6.0F, 8.0F),
                PartPose.offset(-9.0F, -35.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, 0.0F, -3.5F, 6.0F, 18.0F, 7.0F),
                PartPose.offset(3.5F, -18.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-3.0F, 0.0F, -3.5F, 6.0F, 18.0F, 7.0F),
                PartPose.offset(-3.5F, -18.0F, 0.0F));

        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(32, 64).addBox(-3.0F, -3.0F, -1.5F, 6.0F, 6.0F, 3.0F),
                PartPose.offset(0.0F, -28.0F, -4.0F));

        root.addOrReplaceChild("tendril_1",
                CubeListBuilder.create().texOffs(56, 70).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 24.0F, 2.0F),
                PartPose.offset(5.0F, -31.0F, 3.0F));
        root.addOrReplaceChild("tendril_2",
                CubeListBuilder.create().texOffs(56, 70).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 28.0F, 2.0F),
                PartPose.offset(-5.0F, -31.0F, 3.0F));
        root.addOrReplaceChild("tendril_3",
                CubeListBuilder.create().texOffs(66, 70).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 20.0F, 2.0F),
                PartPose.offset(2.5F, -34.0F, 4.0F));
        root.addOrReplaceChild("tendril_4",
                CubeListBuilder.create().texOffs(66, 70).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 22.0F, 2.0F),
                PartPose.offset(-2.5F, -34.0F, 4.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(TobaEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.8F * limbSwingAmount;

        float sway = Mth.sin(ageInTicks * 0.09F) * 0.18F;
        tendril1.zRot = 0.25F + sway;
        tendril2.zRot = -0.25F - sway;
        tendril3.xRot = 0.18F - sway;
        tendril4.xRot = -0.18F + sway;
        core.zRot = ageInTicks * 0.015F;

        switch (entity.getAttackState()) {
            case TobaEntity.SWEEP -> {
                rightArm.xRot = -1.25F;
                rightArm.zRot = -1.8F;
                leftArm.zRot = 0.5F;
            }
            case TobaEntity.SLAM -> {
                rightArm.xRot = -2.5F;
                leftArm.xRot = -2.5F;
                rightArm.zRot = -0.35F;
                leftArm.zRot = 0.35F;
            }
            case TobaEntity.CORRUPTION_WAVE -> {
                rightArm.zRot = -1.3F;
                leftArm.zRot = 1.3F;
                tendril1.xRot = -0.8F;
                tendril2.xRot = -0.8F;
            }
            case TobaEntity.PULL -> {
                rightArm.xRot = -1.4F;
                leftArm.xRot = -1.4F;
                rightArm.yRot = -0.5F;
                leftArm.yRot = 0.5F;
            }
            case TobaEntity.CHANNEL -> {
                rightArm.xRot = -2.1F;
                leftArm.xRot = -2.1F;
                rightArm.zRot = -0.7F;
                leftArm.zRot = 0.7F;
                tendril1.zRot += 0.7F;
                tendril2.zRot -= 0.7F;
            }
            case TobaEntity.STAGGER -> {
                head.xRot = 0.55F;
                rightArm.xRot = 0.8F;
                leftArm.xRot = 0.8F;
            }
            default -> {
                rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.35F * limbSwingAmount;
                leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.35F * limbSwingAmount;
            }
        }
    }
}
