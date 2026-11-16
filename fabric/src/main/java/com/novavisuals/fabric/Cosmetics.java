package com.novavisuals.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Косметика Nova Visuals (бачиш тільки ти): 6 пар крил, 6 капелюхів, власні крила/капелюхи з PNG.
 * Крила: двосторонні спрайти з помахом. Капелюхи: блокові моделі. Усе можна фарбувати відтінком і збільшувати.
 */
public final class Cosmetics {
    private static final String NS = "novavisuals";
    private static final ResourceLocation[] WING_TEX = new ResourceLocation[CosmeticDefs.WING_IDS.length];
    private static final ModelLayerLocation[] HAT_LAYER = new ModelLayerLocation[CosmeticDefs.HAT_IDS.length];
    private static final ResourceLocation[] HAT_TEX = new ResourceLocation[CosmeticDefs.HAT_IDS.length];
    private static final String[] HAT_TEX_NAME = {"hat", "crown", "crown_neon", "mage", "samurai", "horns"};

    static {
        for (int i = 0; i < WING_TEX.length; i++) WING_TEX[i] = new ResourceLocation(NS, "textures/entity/wing_" + CosmeticDefs.WING_IDS[i] + ".png");
        for (int i = 0; i < HAT_LAYER.length; i++) {
            HAT_LAYER[i] = new ModelLayerLocation(new ResourceLocation(NS, "hat_" + CosmeticDefs.HAT_IDS[i]), "main");
            HAT_TEX[i] = new ResourceLocation(NS, "textures/entity/" + HAT_TEX_NAME[i] + ".png");
        }
    }

    public static void register() {
        for (int i = 0; i < HAT_LAYER.length; i++) {
            final int k = i;
            EntityModelLayerRegistry.registerModelLayer(HAT_LAYER[i], () -> hatDef(k));
        }
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerRenderer pr) registrationHelper.register(new Layer(pr, context.getModelSet()));
        });
    }

    // ---------- іконки для меню ----------
    private static final java.util.Map<String, ResourceLocation> ICON_CACHE = new java.util.HashMap<>();

    public static ResourceLocation icon(String name) {
        return ICON_CACHE.computeIfAbsent(name, n -> new ResourceLocation(NS, "textures/gui/cos/" + n + ".png"));
    }

    /** Іконка вибраного варіанта (type: 0 крила, 1 капелюх, 2 зброя); null для «Немає» і своєї косметики. */
    public static ResourceLocation iconFor(int type, int sel) {
        String id = CosmeticDefs.id(type, sel);
        return id == null ? null : icon((type == 0 ? "wing_" : type == 1 ? "hat_" : "wpn_") + id);
    }

    // ---------- моделі капелюхів (блоки) ----------
    private static CubeListBuilder box(float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d);
    }

    private static LayerDefinition hatDef(int k) {
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();
        switch (k) {
            case 0 -> {   // китайська шляпа
                r.addOrReplaceChild("a", box(-7f, -9f, -7f, 14f, 1f, 14f), PartPose.ZERO);
                r.addOrReplaceChild("b", box(-5f, -10f, -5f, 10f, 1f, 10f), PartPose.ZERO);
                r.addOrReplaceChild("c", box(-3f, -11f, -3f, 6f, 1f, 6f), PartPose.ZERO);
                r.addOrReplaceChild("d", box(-1f, -12f, -1f, 2f, 1f, 2f), PartPose.ZERO);
            }
            case 1 -> {   // корона аметисту: основа + кристали
                r.addOrReplaceChild("base", box(-4.5f, -10f, -4.5f, 9f, 2f, 9f), PartPose.ZERO);
                float[][] cs = {{-4.5f, -4.5f}, {3.5f, -4.5f}, {-4.5f, 3.5f}, {3.5f, 3.5f}, {-0.5f, -4.5f}, {-0.5f, 3.5f}};
                for (int i = 0; i < cs.length; i++) r.addOrReplaceChild("c" + i, box(cs[i][0], -14f, cs[i][1], 1f, 4f, 1f), PartPose.ZERO);
                r.addOrReplaceChild("mid", box(-1f, -15f, -1f, 2f, 5f, 2f), PartPose.ZERO);
            }
            case 2 -> {   // неонова корона: тонке кільце з високими зубцями
                r.addOrReplaceChild("base", box(-4.5f, -9.5f, -4.5f, 9f, 1f, 9f), PartPose.ZERO);
                float[] xs = {-4.5f, -1.5f, 1.5f, 3.5f};
                for (int i = 0; i < xs.length; i++) {
                    r.addOrReplaceChild("f" + i, box(xs[i], -13f - (i % 2), -4.5f, 1f, 4f, 1f), PartPose.ZERO);
                    r.addOrReplaceChild("b" + i, box(xs[i], -13f - (i % 2), 3.5f, 1f, 4f, 1f), PartPose.ZERO);
                }
                r.addOrReplaceChild("l", box(-4.5f, -14f, -0.5f, 1f, 5f, 1f), PartPose.ZERO);
                r.addOrReplaceChild("rr", box(3.5f, -14f, -0.5f, 1f, 5f, 1f), PartPose.ZERO);
            }
            case 3 -> {   // капелюх мага
                r.addOrReplaceChild("brim", box(-7f, -9f, -7f, 14f, 1f, 14f), PartPose.ZERO);
                r.addOrReplaceChild("c1", box(-4f, -12f, -4f, 8f, 3f, 8f), PartPose.ZERO);
                r.addOrReplaceChild("c2", box(-3f, -15f, -3f, 6f, 3f, 6f), PartPose.ZERO);
                r.addOrReplaceChild("c3", box(-2f, -18f, -2f, 4f, 3f, 4f), PartPose.ZERO);
                r.addOrReplaceChild("c4", box(-1f, -21f, -1f, 2f, 3f, 2f), PartPose.offsetAndRotation(0f, 0f, 0f, 0f, 0f, 0.35f));
            }
            case 4 -> {   // самурайський шолом
                r.addOrReplaceChild("dome", box(-4.5f, -11f, -4.5f, 9f, 3f, 9f), PartPose.ZERO);
                r.addOrReplaceChild("rim", box(-5.5f, -8.5f, -5.5f, 11f, 1f, 11f), PartPose.ZERO);
                r.addOrReplaceChild("plate", box(-3f, -9.5f, -5.5f, 6f, 2f, 1f), PartPose.ZERO);
                r.addOrReplaceChild("hl", box(-0.5f, -6f, 0f, 1f, 6f, 1f), PartPose.offsetAndRotation(-3.5f, -10f, -5.5f, 0f, 0f, 0.5f));
                r.addOrReplaceChild("hr", box(-0.5f, -6f, 0f, 1f, 6f, 1f), PartPose.offsetAndRotation(3.5f, -10f, -5.5f, 0f, 0f, -0.5f));
            }
            default -> {   // диявольські роги
                r.addOrReplaceChild("l1", box(-1.5f, -4f, -1.5f, 3f, 4f, 3f), PartPose.offsetAndRotation(-3.6f, -7.5f, -1f, 0f, 0f, 0.35f));
                r.addOrReplaceChild("l2", box(-1f, -4f, -1f, 2f, 4f, 2f), PartPose.offsetAndRotation(-5.4f, -11f, -1f, 0f, 0f, 0.7f));
                r.addOrReplaceChild("r1", box(-1.5f, -4f, -1.5f, 3f, 4f, 3f), PartPose.offsetAndRotation(3.6f, -7.5f, -1f, 0f, 0f, -0.35f));
                r.addOrReplaceChild("r2", box(-1f, -4f, -1f, 2f, 4f, 2f), PartPose.offsetAndRotation(5.4f, -11f, -1f, 0f, 0f, -0.7f));
            }
        }
        return LayerDefinition.create(m, 64, 64);
    }

    // ---------- шар на моделі гравця ----------
    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ModelPart[] hats = new ModelPart[HAT_LAYER.length];

        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
            super(parent);
            for (int i = 0; i < hats.length; i++) hats[i] = models.bakeLayer(HAT_LAYER[i]);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p,
                           float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            Settings.Data d = Settings.d;
            if (!d.cosOn || p != Minecraft.getInstance().player || p.isInvisible()) return;
            int lt = d.cosGlow ? LightTexture.FULL_BRIGHT : light;
            if (d.cosWings > 0) wings(pose, buf, lt, p, age, limbSwingAmount);
            if (d.cosHat > 0) hat(pose, buf, lt);
        }

        private void wings(PoseStack pose, MultiBufferSource buf, int lt, AbstractClientPlayer p, float age, float limb) {
            Settings.Data d = Settings.d;
            ResourceLocation tex = null;
            String id = CosmeticDefs.id(CosmeticDefs.WINGS, d.cosWings);
            if (id != null) { for (int i = 0; i < CosmeticDefs.WING_IDS.length; i++) if (CosmeticDefs.WING_IDS[i].equals(id)) tex = WING_TEX[i]; }
            else tex = CosTex.get(CosmeticDefs.custom(CosmeticDefs.WINGS, d.cosWings));
            if (tex == null) return;   // своя косметика без PNG малюється частинками
            float sc = Math.max(0.5f, d.cosScale);
            float flap = d.cosFlap ? (float) Math.sin(age * 0.2f) * (limb > 0.1f ? 14f : 8f) : 0f;
            float w = 26f / 16f * sc, hUp = 0.66f * w, hDown = 0.34f * w;
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(tex));
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 1.2f / 16f, 3f / 16f, 2.6f / 16f);
                pose.mulPose(Vector3f.YP.rotationDegrees(-side * (52f + flap)));
                quad(pose, vc, side * w, -hUp, hDown, lt, d.cosR, d.cosG, d.cosB, 1f);
                pose.popPose();
            }
            pose.popPose();
        }

        private void hat(PoseStack pose, MultiBufferSource buf, int lt) {
            Settings.Data d = Settings.d;
            float sc = Math.max(0.5f, d.cosScale);
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            int sel = d.cosHat;
            String id = CosmeticDefs.id(CosmeticDefs.HATS, sel);
            if (id != null) {
                for (int i = 0; i < CosmeticDefs.HAT_IDS.length; i++) {
                    if (!CosmeticDefs.HAT_IDS[i].equals(id)) continue;
                    pose.scale(sc, sc, sc);
                    hats[i].render(pose, buf.getBuffer(RenderType.entityCutoutNoCull(HAT_TEX[i])), lt, OverlayTexture.NO_OVERLAY, d.cosR, d.cosG, d.cosB, 1f);
                }
            } else {
                ResourceLocation tex = CosTex.get(CosmeticDefs.custom(CosmeticDefs.HATS, sel));
                if (tex != null) {   // свій капелюх із PNG: плоский спрайт над головою
                    float hw = 7f / 16f * sc;
                    quadFlat(pose, buf.getBuffer(RenderType.entityTranslucent(tex)), hw, -22f / 16f * sc, -8f / 16f, lt, d.cosR, d.cosG, d.cosB, 1f);
                }
            }
            pose.popPose();
        }
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int lt, float r, float g, float b, float a) {
        vc.vertex(m, x, y, 0f).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lt).normal(n, 0f, 0f, 1f).endVertex();
    }

    /** Спрайт крила: від x=0 до x1, від yTop до yBot; u йде від кореня крила назовні. */
    private static void quad(PoseStack pose, VertexConsumer vc, float x1, float yTop, float yBot, int lt, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vtx(vc, m, n, 0f, yTop, 0f, 0f, lt, r, g, b, a);
        vtx(vc, m, n, x1, yTop, 1f, 0f, lt, r, g, b, a);
        vtx(vc, m, n, x1, yBot, 1f, 1f, lt, r, g, b, a);
        vtx(vc, m, n, 0f, yBot, 0f, 1f, lt, r, g, b, a);
    }

    private static void quadFlat(PoseStack pose, VertexConsumer vc, float hw, float yTop, float yBot, int lt, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vtx(vc, m, n, -hw, yTop, 0f, 0f, lt, r, g, b, a);
        vtx(vc, m, n, hw, yTop, 1f, 0f, lt, r, g, b, a);
        vtx(vc, m, n, hw, yBot, 1f, 1f, lt, r, g, b, a);
        vtx(vc, m, n, -hw, yBot, 0f, 1f, lt, r, g, b, a);
    }

    private Cosmetics() { }
}
