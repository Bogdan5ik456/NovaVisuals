package com.novavisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Random;

/** Частинки при ударі, підсвічування цілі, іскри над предметами й анімація руки (тільки візуально, тільки в тебе). */
public final class HitFx {
    private static final Random R = new Random();

    public static void burst(Minecraft mc, LivingEntity t) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        double x = t.getX(), y = t.getY() + t.getBbHeight() * 0.6, z = t.getZ();
        SimpleParticleType type = switch (Settings.d.hitStyle) {
            case 1 -> ParticleTypes.ENCHANTED_HIT;
            case 2 -> ParticleTypes.FIREWORK;
            case 3 -> ParticleTypes.HEART;
            case 4 -> ParticleTypes.SOUL;
            default -> ParticleTypes.CRIT;
        };
        for (int i = 0; i < 16; i++) {
            lv.addParticle(type, x, y, z, (R.nextDouble() - 0.5) * 0.5, R.nextDouble() * 0.4, (R.nextDouble() - 0.5) * 0.5);
        }
    }

    /** Кільце іскор навколо цілі, яку ти вдарив. Частинки малюються як звичайно, тобто крізь стіни не видно. */
    public static void ring(Minecraft mc, LivingEntity t, int phase) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        double rad = t.getBbWidth() * 0.9 + 0.2;
        for (int i = 0; i < 6; i++) {
            double a = phase * 0.45 + i * Math.PI / 3.0;
            lv.addParticle(ParticleTypes.END_ROD, t.getX() + Math.cos(a) * rad, t.getY() + 0.1 + (phase % 10) * 0.12,
                    t.getZ() + Math.sin(a) * rad, 0, 0.01, 0);
        }
    }

    /** Іскри над предметами на землі поруч (до 24 блоків). */
    public static void itemGlow(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) return;
        int n = 0;
        for (ItemEntity it : lv.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(24.0))) {
            if (++n > 30) break;
            lv.addParticle(ParticleTypes.END_ROD, it.getX() + (R.nextDouble() - 0.5) * 0.3, it.getY() + 0.5 + R.nextDouble() * 0.3,
                    it.getZ() + (R.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
        }
    }

    /** Анімація предметів у руках: 1 хитання, 2 левітація, 3 нахил. */
    public static void handAnim(PoseStack ps) {
        int a = Settings.d.handAnim;
        if (a == 0) return;
        float t = (System.nanoTime() / 1_000_000L % 100000L) / 1000f;
        switch (a) {
            case 1 -> {
                ps.translate(Math.sin(t * 1.6) * 0.02, Math.cos(t * 2.2) * 0.015, 0);
                ps.mulPose(Vector3f.ZP.rotationDegrees((float) Math.sin(t * 1.6) * 2.5f));
            }
            case 2 -> ps.translate(0, Math.sin(t * 2.0) * 0.03, 0);
            default -> ps.mulPose(Vector3f.XP.rotationDegrees((float) Math.sin(t * 1.2) * 6f));
        }
    }

    private HitFx() { }
}
