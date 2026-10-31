package com.novavisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/** Ефект при вбивстві: частинки й звук, видно тільки тобі. */
public final class KillFx {
    public static void play(Minecraft mc, LivingEntity target) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        playAt(lv, target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ());
    }

    public static void playAt(ClientLevel lv, double x, double y, double z) {
        SimpleParticleType type = switch (Settings.d.killStyle) {
            case 1 -> ParticleTypes.SOUL;
            case 2 -> ParticleTypes.FLAME;
            case 3 -> ParticleTypes.FIREWORK;
            case 4 -> ParticleTypes.HEART;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
        Random r = new Random();
        for (int i = 0; i < 40; i++) {
            // force = true: частинка створюється навіть коли в налаштуваннях гри частинки на «Мінімум»
            lv.addParticle(type, true, x, y, z, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 0.6);
        }
        if (Settings.d.killSound) lv.playLocalSound(x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f, false);
    }

    private KillFx() { }
}
