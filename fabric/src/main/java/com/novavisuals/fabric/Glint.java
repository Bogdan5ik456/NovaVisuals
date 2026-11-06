package com.novavisuals.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.InputStream;

/** Custom Enchant: підмінює текстуру блиску чарів на твій колір (на броні й предметах, видно тільки тобі). */
public final class Glint {
    private static final ResourceLocation LOC = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static NativeImage base;
    private static DynamicTexture tex;
    private static int lastKey = -1;

    public static void tick(Minecraft mc) {
        Settings.Data d = Settings.d;
        if (!d.glintOn) {
            if (tex != null) { mc.getTextureManager().register(LOC, new SimpleTexture(LOC)); tex = null; lastKey = -1; }
            return;
        }
        float r = d.glintR, g = d.glintG, b = d.glintB;
        int key;
        if (d.glintRainbow) {
            int rgb = Mth.hsvToRgb((System.currentTimeMillis() % 4000L) / 4000f, 0.9f, 1f);
            r = ((rgb >> 16) & 255) / 255f; g = ((rgb >> 8) & 255) / 255f; b = (rgb & 255) / 255f;
            key = (int) ((System.currentTimeMillis() / 150L) & 0x7FFFFFFF);
        } else {
            key = (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        }
        boolean lost = tex != null && mc.getTextureManager().getTexture(LOC) != tex;   // після перезавантаження ресурсів
        if (tex != null && !lost && key == lastKey) return;
        apply(mc, r, g, b);
        lastKey = key;
    }

    private static void apply(Minecraft mc, float cr, float cg, float cb) {
        try {
            if (base == null) {
                try (InputStream in = mc.getResourceManager().getResource(LOC).getInputStream()) { base = NativeImage.read(in); }
            }
            NativeImage img = new NativeImage(base.getWidth(), base.getHeight(), false);
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    int p = base.getPixelRGBA(x, y);
                    int a = (p >>> 24) & 255, bl = (p >> 16) & 255, gr = (p >> 8) & 255, rd = p & 255;
                    float lum = Math.min(1f, (rd + gr + bl) / (3f * 255f) * 1.6f);
                    int nr = Math.min(255, Math.round(lum * cr * 255f)), ng = Math.min(255, Math.round(lum * cg * 255f)), nb = Math.min(255, Math.round(lum * cb * 255f));
                    img.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
                }
            }
            DynamicTexture nt = new DynamicTexture(img);
            mc.getTextureManager().register(LOC, nt);
            tex = nt;
        } catch (Exception e) {
            Settings.d.glintOn = false;   // щоб не сипати помилками щотіку
        }
    }

    private Glint() { }
}
