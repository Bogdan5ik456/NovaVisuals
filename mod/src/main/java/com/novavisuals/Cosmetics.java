package com.novavisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Косметика на голову: китайський капелюх і корона (бачиш тільки ти). */
public final class Cosmetics {
    private static final ModelLayerLocation HAT = new ModelLayerLocation(new ResourceLocation("novavisuals", "hat"), "main");
    private static final ModelLayerLocation CROWN = new ModelLayerLocation(new ResourceLocation("novavisuals", "crown"), "main");
    private static final ResourceLocation HAT_TEX = new ResourceLocation("novavisuals", "textures/entity/hat.png");
    private static final ResourceLocation CROWN_TEX = new ResourceLocation("novavisuals", "textures/entity/crown.png");

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(HAT, Cosmetics::hatDef);
        e.registerLayerDefinition(CROWN, Cosmetics::crownDef);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : new String[]{"default", "slim"}) {
            PlayerRenderer r = e.getSkin(skin);
            if (r != null) r.addLayer(new Layer(r, e.getEntityModels()));
        }
    }

    private static LayerDefinition hatDef() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition root = m.getRoot();
        root.addOrReplaceChild("a", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -9f, -7f, 14f, 1f, 14f), PartPose.ZERO);
        root.addOrReplaceChild("b", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -10f, -5f, 10f, 1f, 10f), PartPose.ZERO);
        root.addOrReplaceChild("c", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -11f, -3f, 6f, 1f, 6f), PartPose.ZERO);
        root.addOrReplaceChild("d", CubeListBuilder.create().texOffs(0, 0).addBox(-1f, -12f, -1f, 2f, 1f, 2f), PartPose.ZERO);
        return LayerDefinition.create(m, 64, 64);
    }

    private static LayerDefinition crownDef() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition root = m.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -10f, -4.5f, 9f, 2f, 9f), PartPose.ZERO);
        root.addOrReplaceChild("s1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -12f, -4.5f, 1f, 2f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("s2", CubeListBuilder.create().texOffs(0, 0).addBox(3.5f, -12f, -4.5f, 1f, 2f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("s3", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -12f, 3.5f, 1f, 2f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("s4", CubeListBuilder.create().texOffs(0, 0).addBox(3.5f, -12f, 3.5f, 1f, 2f, 1f), PartPose.ZERO);
        return LayerDefinition.create(m, 64, 64);
    }

    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ModelPart hat, crown;

        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
            super(parent);
            this.hat = models.bakeLayer(HAT);
            this.crown = models.bakeLayer(CROWN);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p,
                           float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            if (Settings.d.hat == 0 || p != Minecraft.getInstance().player || p.isInvisible()) return;
            ModelPart part = Settings.d.hat == 1 ? hat : crown;
            ResourceLocation tex = Settings.d.hat == 1 ? HAT_TEX : CROWN_TEX;
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            part.render(pose, buf.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private Cosmetics() { }
}
