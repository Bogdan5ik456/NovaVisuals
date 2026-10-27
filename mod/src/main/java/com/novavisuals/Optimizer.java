package com.novavisuals;

import net.minecraft.client.AmbientOcclusionStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.network.chat.TextComponent;

/** Кнопка «Optimize»: ставить налаштування на швидкодію й вміє повернути попередні. */
public final class Optimizer {
    public static void toggle() {
        if (Settings.d.optimized) restore(); else apply();
    }

    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        Settings.Data d = Settings.d;
        if (!d.optimized) {   // запам'ятовуємо твої налаштування
            d.sRender = o.renderDistance;
            d.sGraphics = o.graphicsMode.ordinal();
            d.sClouds = o.renderClouds.ordinal();
            d.sParticles = o.particles.ordinal();
            d.sAo = o.ambientOcclusion.ordinal();
            d.sFps = o.framerateLimit;
            d.sVsync = o.enableVsync;
            d.sBlend = o.biomeBlendRadius;
            d.sShadows = o.entityShadows;
        }
        o.renderDistance = Math.min(o.renderDistance, 8);
        o.graphicsMode = GraphicsStatus.FAST;
        o.renderClouds = CloudStatus.OFF;
        o.particles = ParticleStatus.MINIMAL;
        o.ambientOcclusion = AmbientOcclusionStatus.OFF;
        o.framerateLimit = 260;   // 260 = без обмеження
        o.enableVsync = false;
        o.biomeBlendRadius = 0;
        o.entityShadows = false;
        d.optimized = true;
        finish(mc, "Оптимізацію застосовано");
    }

    public static void restore() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        Settings.Data d = Settings.d;
        o.renderDistance = d.sRender;
        o.graphicsMode = GraphicsStatus.values()[Math.min(d.sGraphics, GraphicsStatus.values().length - 1)];
        o.renderClouds = CloudStatus.values()[Math.min(d.sClouds, CloudStatus.values().length - 1)];
        o.particles = ParticleStatus.values()[Math.min(d.sParticles, ParticleStatus.values().length - 1)];
        o.ambientOcclusion = AmbientOcclusionStatus.values()[Math.min(d.sAo, AmbientOcclusionStatus.values().length - 1)];
        o.framerateLimit = d.sFps;
        o.enableVsync = d.sVsync;
        o.biomeBlendRadius = d.sBlend;
        o.entityShadows = d.sShadows;
        d.optimized = false;
        finish(mc, "Налаштування відновлено");
    }

    private static void finish(Minecraft mc, String msg) {
        mc.options.save();
        mc.getWindow().updateVsync(mc.options.enableVsync);
        if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
        if (mc.player != null) mc.player.displayClientMessage(new TextComponent("[Nova] " + msg), true);
        Settings.save();
    }

    private Optimizer() { }
}
