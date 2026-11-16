package com.novavisuals.fabric121;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ефекти при ударі та вбивстві, бризки у воді, підсвітка цілі, іскри над предметами (тільки візуально, тільки в тебе).
 * Форми: 0 вибух, 1 фонтан, 2 спіраль, 3 кільця, 4 стовп, 5 крила, 6 торнадо. Перенесено з версії 1.18.2.
 */
public final class Fx {
    private static final Random R = new Random();
    private static final int[] LIFE = {1, 8, 20, 12, 10, 20, 30};
    private static final int[] HIT_MAP = {0, 3, 2, 1};

    private static final class Act { int shape, style, life, age; boolean kill; double x, y, z; float yaw; }
    private static final List<Act> acts = new ArrayList<>();
    private static final Map<Integer, Long> done = new HashMap<>();

    /** Тип частинки за номером зі Settings.PART_NAMES (колірний пил у 1.21.11 поки не підтримується). */
    public static ParticleOptions opt(int style) {
        return switch (style) {
            case 1 -> ParticleTypes.ENCHANTED_HIT;
            case 2 -> ParticleTypes.FIREWORK;
            case 3 -> ParticleTypes.HEART;
            case 4 -> ParticleTypes.SOUL;
            case 5 -> ParticleTypes.FLAME;
            case 6 -> ParticleTypes.TOTEM_OF_UNDYING;
            case 7 -> ParticleTypes.END_ROD;
            case 8 -> ParticleTypes.ELECTRIC_SPARK;
            case 9 -> ParticleTypes.DRAGON_BREATH;
            case 10 -> ParticleTypes.DAMAGE_INDICATOR;
            default -> ParticleTypes.CRIT;
        };
    }

    private static void put(ClientLevel lv, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        double sp = Settings.d.fxSpeed;
        lv.addParticle(o, x, y, z, vx * sp, vy * sp, vz * sp);
    }

    private static int n(int base, boolean kill) {
        return Math.max(1, (int) Math.round(base * Settings.d.fxAmount * (kill ? 1.0 : 0.6)));
    }

    private static double jit(double d) { return (R.nextDouble() - 0.5) * 2.0 * d; }

    private static void start(boolean kill, int shape, int style, double x, double y, double z, float yaw) {
        Act a = new Act();
        a.kill = kill; a.shape = Math.max(0, Math.min(6, shape)); a.style = style; a.life = LIFE[a.shape];
        a.x = x; a.y = y; a.z = z; a.yaw = yaw;
        acts.add(a);
    }

    public static void hit(Minecraft mc, LivingEntity t) {
        if (mc.level == null) return;
        start(false, HIT_MAP[Math.max(0, Math.min(3, Settings.d.hitShape))], Settings.d.hitStyle, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), t.getYRot());
    }

    /** Ефект вбивства: працює на мобах і гравцях. Одну смерть двічі не рахуємо. */
    public static void kill(Minecraft mc, LivingEntity t) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        long now = lv.getGameTime();
        Long last = done.get(t.getId());
        if (last != null && now - last < 80L) return;
        done.put(t.getId(), now);
        if (done.size() > 64) done.values().removeIf(v -> now - v > 200L);
        start(true, Settings.d.killShape, Settings.d.killStyle, t.getX(), t.getY(), t.getZ(), t.getYRot());
        if (Settings.d.killSound) lv.playLocalSound(t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f, false);
    }

    /** Кнопка «Test Effects»: показує удар і вбивство за два з половиною блоки перед тобою. */
    public static void test(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Vec3 look = mc.player.getLookAngle();
        double x = mc.player.getX() + look.x * 2.5, y = mc.player.getY(), z = mc.player.getZ() + look.z * 2.5;
        float yaw = mc.player.getYRot() + 180f;
        start(false, HIT_MAP[Math.max(0, Math.min(3, Settings.d.hitShape))], Settings.d.hitStyle, x, y + 1.0, z, yaw);
        start(true, Settings.d.killShape, Settings.d.killStyle, x, y, z, yaw);
        if (Settings.d.killSound) mc.level.playLocalSound(x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f, false);
    }

    /** Бризки, коли стрибаєш у воду: сила залежить від швидкості падіння. */
    public static void splash(Minecraft mc, double x, double y, double z, double speed) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        int c = (int) Math.min(90, (14 + speed * 70) * Settings.d.fxAmount);
        for (int i = 0; i < c; i++) {
            double ang = R.nextDouble() * Math.PI * 2.0, sp = 0.05 + R.nextDouble() * 0.25;
            lv.addParticle(ParticleTypes.SPLASH, x + Math.cos(ang) * 0.3, y + 0.1, z + Math.sin(ang) * 0.3,
                    Math.cos(ang) * sp, 0.15 + R.nextDouble() * (0.2 + speed * 0.4), Math.sin(ang) * sp);
        }
        for (int i = 0; i < c / 2; i++) lv.addParticle(ParticleTypes.BUBBLE, x + jit(0.5), y - 0.2, z + jit(0.5), jit(0.1), 0.05 + R.nextDouble() * 0.1, jit(0.1));
        for (int i = 0; i < 24; i++) {
            double ang = i * Math.PI * 2.0 / 24.0;
            lv.addParticle(ParticleTypes.SPLASH, x + Math.cos(ang) * 0.9, y + 0.05, z + Math.sin(ang) * 0.9, Math.cos(ang) * 0.12, 0.05, Math.sin(ang) * 0.12);
        }
    }

    /** Кільце іскор навколо цілі, яку ти вдарив. */
    public static void ring(Minecraft mc, LivingEntity t, int phase) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        double rad = t.getBbWidth() * 0.9 + 0.2;
        for (int i = 0; i < 6; i++) {
            double a = phase * 0.45 + i * Math.PI / 3.0;
            lv.addParticle(ParticleTypes.END_ROD, t.getX() + Math.cos(a) * rad, t.getY() + 0.1 + (phase % 10) * 0.12, t.getZ() + Math.sin(a) * rad, 0, 0.01, 0);
        }
    }

    /** Іскри над предметами на землі поруч (до 24 блоків). */
    public static void itemGlow(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) return;
        int n = 0;
        AABB box = mc.player.getBoundingBox().inflate(24.0);
        for (ItemEntity it : lv.getEntitiesOfClass(ItemEntity.class, box)) {
            if (++n > 30) break;
            lv.addParticle(ParticleTypes.END_ROD, it.getX() + jit(0.15), it.getY() + 0.5 + R.nextDouble() * 0.3, it.getZ() + jit(0.15), 0, 0.02, 0);
        }
    }

    /** Викликається щотіку: випускає частинки активних ефектів. */
    public static void tick(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null) { acts.clear(); return; }
        for (Iterator<Act> it = acts.iterator(); it.hasNext();) {
            Act a = it.next();
            step(lv, a);
            if (++a.age >= a.life) it.remove();
        }
    }

    private static void step(ClientLevel lv, Act a) {
        ParticleOptions o = opt(a.style);
        switch (a.shape) {
            case 0 -> {
                int c = n(24, a.kill);
                for (int i = 0; i < c; i++) {
                    double th = R.nextDouble() * Math.PI * 2.0, ph = Math.acos(2.0 * R.nextDouble() - 1.0), sp = 0.12 + R.nextDouble() * 0.2;
                    put(lv, o, a.x, a.y + 0.5, a.z, Math.sin(ph) * Math.cos(th) * sp, Math.cos(ph) * sp + 0.05, Math.sin(ph) * Math.sin(th) * sp);
                }
            }
            case 1 -> {
                int c = n(6, a.kill);
                for (int i = 0; i < c; i++) put(lv, o, a.x + jit(0.2), a.y + 0.2, a.z + jit(0.2), jit(0.06), 0.25 + R.nextDouble() * 0.25, jit(0.06));
            }
            case 2 -> {
                int arms = a.kill ? 2 : 1;
                for (int arm = 0; arm < arms; arm++) {
                    double ang = a.age * 0.55 + arm * Math.PI;
                    put(lv, o, a.x + Math.cos(ang) * 0.7, a.y + a.age * 0.1, a.z + Math.sin(ang) * 0.7, 0, 0.02, 0);
                }
            }
            case 3 -> {
                double rad = 0.3 + a.age * 0.18;
                int c = n(18, a.kill);
                for (int i = 0; i < c; i++) {
                    double ang = i * Math.PI * 2.0 / c;
                    put(lv, o, a.x + Math.cos(ang) * rad, a.y + 0.2, a.z + Math.sin(ang) * rad, Math.cos(ang) * 0.02, 0.01, Math.sin(ang) * 0.02);
                }
            }
            case 4 -> {
                int c = n(10, a.kill);
                for (int i = 0; i < c; i++) put(lv, o, a.x + jit(0.18), a.y + R.nextDouble() * 7.0, a.z + jit(0.18), 0, 0.08, 0);
            }
            case 5 -> {
                if (a.age % 3 != 0) return;
                double yaw = Math.toRadians(a.yaw);
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i <= 24; i++) {
                        double t = i / 24.0;
                        double lat = side * (0.3 + 1.4 * t);
                        double vert = 1.0 + 0.9 * Math.sin(Math.PI * (0.1 + 0.75 * t)) * (1.0 - 0.25 * t) - 0.5 * t * t;
                        double back = -0.3 - 0.3 * t;
                        put(lv, o, a.x + rx * lat + fx * back, a.y + vert, a.z + rz * lat + fz * back, 0, 0.004, 0);
                    }
                }
            }
            default -> {
                int c = n(6, a.kill);
                for (int i = 0; i < c; i++) {
                    double h = R.nextDouble() * 3.2, rad = 0.15 + h * 0.32, ang = a.age * 0.6 + h * 2.4;
                    put(lv, o, a.x + Math.cos(ang) * rad, a.y + h, a.z + Math.sin(ang) * rad, -Math.sin(ang) * 0.03, 0.03, Math.cos(ang) * 0.03);
                }
            }
        }
    }

    private Fx() { }
}
