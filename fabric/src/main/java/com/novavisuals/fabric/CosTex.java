package com.novavisuals.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Текстури своєї косметики (PNG з диска), що завантажуються в гру один раз. */
public final class CosTex {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    public static ResourceLocation get(Settings.Custom c) {
        if (c == null || c.png == null || c.png.isEmpty()) return null;
        if (CACHE.containsKey(c.id)) return CACHE.get(c.id);
        ResourceLocation rl = null;
        try {
            Path p = Path.of(c.png);
            if (Files.exists(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    NativeImage img = NativeImage.read(in);
                    rl = new ResourceLocation("novavisuals", "custom/" + c.id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                }
            }
        } catch (Exception ignored) { rl = null; }
        CACHE.put(c.id, rl);
        return rl;
    }

    public static void forget(String id) { CACHE.remove(id); }

    private CosTex() { }
}
