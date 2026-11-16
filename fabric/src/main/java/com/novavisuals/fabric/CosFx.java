package com.novavisuals.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Косметика частинками (тільки візуально, тільки в тебе): анімації (танець, медитація, летюче сяйво, сліди, AFK),
 * сяйво навколо зброї в руці, а там, де немає 3D-моделей, ще й крила та капелюхи.
 */
public final class CosFx {
    /** true: крила й капелюхи малюються частинками (немає 3D-шару). */
    public static boolean particleModels = false;

    private static final Random R = new Random();
    private static int ticks, afk, still, dance;
    private static float lyaw;
    private static boolean gPrev;

    public static ParticleOptions pal(int p) {
        return switch (p) {
            case 1 -> ParticleTypes.END_ROD;
            case 2 -> ParticleTypes.FLAME;
            case 3 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 4 -> ParticleTypes.HAPPY_VILLAGER;
            case 5 -> ParticleTypes.ELECTRIC_SPARK;
            case 6 -> ParticleTypes.SNOWFLAKE;
            case 7 -> ParticleTypes.SOUL;
            default -> ParticleTypes.WITCH;
        };
    }

    public static void startDance() { dance = 120; }

    /** Пресети ефекту вбивства з аркуша: 1 кривавий сплеск, 2 душі ворога, 3 вибух частинок. */
    public static void applyKill(int k) {
        Settings.Data d = Settings.d;
        if (k <= 0) return;
        d.killFx = true;
        d.fxRainbow = false;
        switch (k) {
            case 1 -> { d.killStyle = 10; d.killShape = 0; d.fxR = 0.75f; d.fxG = 0.02f; d.fxB = 0.05f; d.fxSize = 1.6f; }
            case 2 -> { d.killStyle = 4; d.killShape = 2; }
            default -> { d.killStyle = 8; d.killShape = 0; }
        }
        Settings.save();
    }

    private static boolean customNoPng(int type, int sel) {
        Settings.Custom c = CosmeticDefs.custom(type, sel);
        return c != null && (c.png == null || c.png.isEmpty());
    }

    private static void at(ClientLevel lv, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        lv.addParticle(o, x, y, z, vx, vy, vz);
    }

    public static void tick(Minecraft mc) {
        Settings.Data d = Settings.d;
        ClientLevel lv = mc.level;
        LocalPlayer p = mc.player;
        if (lv == null || p == null) return;
        ticks++;
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        boolean moving = Math.sqrt(dx * dx + dz * dz) > 0.015;
        float yaw = p.getYRot();
        boolean turned = Math.abs(Mth.wrapDegrees(yaw - lyaw)) > 1f;
        lyaw = yaw;
        if (moving || turned || p.swinging || p.isUsingItem()) afk = 0; else afk++;
        boolean sneak = p.isShiftKeyDown();
        if (sneak && !moving) still++; else still = 0;

        // G запускає танець (якщо увімкнена анімація «Емоція танцю»)
        long win = GLFW.glfwGetCurrentContext();
        boolean g = win != 0L && mc.screen == null && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        if (g && !gPrev && d.animDance) startDance();
        gPrev = g;

        if (!d.cosOn) return;
        double yb = Math.toRadians(p.yBodyRot);
        double fx = -Math.sin(yb), fz = Math.cos(yb), rx = -Math.cos(yb), rz = -Math.sin(yb);
        double x = p.getX(), y = p.getY(), z = p.getZ();
        boolean flying = p.isFallFlying() || p.getAbilities().flying;
        double flap = d.cosFlap ? Math.sin(ticks * 0.2) * 0.25 + (flying ? 0.3 : 0.0) : 0.0;
        double sc = Math.max(0.5, d.cosScale);

        // крила й капелюхи частинками
        if (ticks % 3 == 0) {
            int w = d.cosWings;
            if (w > 0 && (particleModels || customNoPng(CosmeticDefs.WINGS, w)))
                wings(lv, CosFx.pal(CosmeticDefs.pal(CosmeticDefs.WINGS, w)), x, y, z, fx, fz, rx, rz, flap, sc);
            int h = d.cosHat;
            if (h > 0 && (particleModels || customNoPng(CosmeticDefs.HATS, h)))
                hat(lv, CosFx.pal(CosmeticDefs.pal(CosmeticDefs.HATS, h)), CosmeticDefs.id(CosmeticDefs.HATS, h), x, y + (sneak ? 1.55 : 1.75), z, sc);
        }

        // сяйво навколо зброї в руці
        if (d.cosWeapon > 0 && !p.getMainHandItem().isEmpty() && ticks % 2 == 0) {
            ParticleOptions o = pal(CosmeticDefs.pal(CosmeticDefs.WEAPONS, d.cosWeapon));
            for (int i = 0; i < 2; i++)
                at(lv, o, x + rx * 0.42 + fx * 0.5 + (R.nextDouble() - 0.5) * 0.25, y + 0.9 + R.nextDouble() * 0.5, z + rz * 0.42 + fz * 0.5 + (R.nextDouble() - 0.5) * 0.25, 0, 0.01, 0);
        }

        ParticleOptions ap = pal(d.cosPal);
        if (dance > 0) {   // емоція танцю: ноти навколо
            dance--;
            double a = ticks * 0.45;
            at(lv, ParticleTypes.NOTE, x + Math.cos(a) * 0.8, y + 1.2 + Math.sin(ticks * 0.3) * 0.5 + 0.5, z + Math.sin(a) * 0.8, R.nextDouble(), 0, 0);
            if (ticks % 4 == 0) at(lv, ParticleTypes.NOTE, x + Math.cos(a + 3.1) * 0.7, y + 2.1, z + Math.sin(a + 3.1) * 0.7, R.nextDouble(), 0, 0);
        }
        if (d.animMeditate && still > 30) {   // медитація: спіраль аури вгору
            for (int i = 0; i < 2; i++) {
                double a = ticks * 0.5 + i * Math.PI, hh = (ticks % 24) / 24.0 * 2.0;
                at(lv, ap, x + Math.cos(a) * 0.65, y + hh, z + Math.sin(a) * 0.65, 0, 0.02, 0);
            }
            if (ticks % 6 == 0) at(lv, ParticleTypes.ENCHANT, x + (R.nextDouble() - 0.5), y + 1.6, z + (R.nextDouble() - 0.5), 0, -0.1, 0);
        }
        if (d.animFly && flying)   // летюче сяйво: шлейф позаду
            for (int i = 0; i < 3; i++) at(lv, ap, x - fx * (0.3 + R.nextDouble() * 0.5) + (R.nextDouble() - 0.5) * 0.5, y + 0.8 + R.nextDouble() * 0.8, z - fz * (0.3 + R.nextDouble() * 0.5) + (R.nextDouble() - 0.5) * 0.5, 0, 0.0, 0);
        if (d.animTrail && moving && Math.abs(p.getDeltaMovement().y) < 0.08 && ticks % 3 == 0) {   // сліди при ходьбі
            double side = (ticks / 3 % 2 == 0 ? 1 : -1) * 0.18;
            at(lv, ap, x + rx * side, y + 0.05, z + rz * side, 0, 0.01, 0);
            at(lv, ap, x + rx * side, y + 0.12, z + rz * side, 0, 0.02, 0);
        }
        if (d.animAfk && afk > 400 && ticks % 8 == 0) {   // AFK: кільце аури й «z» над головою
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + ticks * 0.05;
                at(lv, ap, x + Math.cos(a) * 0.6, y + 0.2 + (ticks % 40) / 40.0 * 1.6, z + Math.sin(a) * 0.6, 0, 0.01, 0);
            }
            at(lv, ParticleTypes.CLOUD, x + rx * 0.3, y + 2.0, z + rz * 0.3, 0, 0.04, 0);
        }
    }

    // ---------- форми ----------
    private static void wings(ClientLevel lv, ParticleOptions o, double x, double y, double z, double fx, double fz, double rx, double rz, double flap, double sc) {
        for (int side = -1; side <= 1; side += 2) {
            for (int ray = 0; ray < 5; ray++) {
                double f = ray / 4.0;
                double ang = Math.toRadians(70 - f * 115 + flap * 20);
                double len = (0.9 + 0.6 * Math.sin(Math.PI * (0.15 + 0.7 * f))) * sc;
                for (int s = 1; s <= 4; s++) {
                    double t = s / 4.0;
                    double lat = side * (0.2 + Math.cos(ang) * len * t * (1.0 - 0.2 * flap));
                    double vert = 1.25 + Math.sin(ang) * len * t * 0.8;
                    double back = 0.22 + 0.35 * t + flap * 0.2 * t;
                    at(lv, o, x + rx * lat - fx * back, y + vert, z + rz * lat - fz * back, 0, 0, 0);
                }
            }
        }
    }

    private static void ring(ClientLevel lv, ParticleOptions o, double x, double y, double z, double r, int n, double phase) {
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            at(lv, o, x + Math.cos(a) * r, y, z + Math.sin(a) * r, 0, 0, 0);
        }
    }

    private static void hat(ClientLevel lv, ParticleOptions o, String id, double x, double y, double z, double sc) {
        double ph = ticks * 0.04;
        if (id == null) { ring(lv, o, x, y + 0.3, z, 0.32 * sc, 8, ph); return; }
        switch (id) {
            case "chinese" -> { ring(lv, o, x, y + 0.3, z, 0.6 * sc, 10, ph); ring(lv, o, x, y + 0.5, z, 0.3 * sc, 6, -ph); at(lv, o, x, y + 0.62, z, 0, 0, 0); }
            case "amethyst", "neon" -> {
                ring(lv, o, x, y + 0.28, z, 0.3 * sc, 8, ph);
                for (int k = 0; k < 5; k++) { double a = k * Math.PI * 2 / 5 + ph; at(lv, o, x + Math.cos(a) * 0.3 * sc, y + 0.5, z + Math.sin(a) * 0.3 * sc, 0, 0, 0); at(lv, o, x + Math.cos(a) * 0.3 * sc, y + 0.68, z + Math.sin(a) * 0.3 * sc, 0, 0, 0); }
            }
            case "mage" -> {
                ring(lv, o, x, y + 0.3, z, 0.55 * sc, 10, ph);
                ring(lv, o, x, y + 0.5, z, 0.38 * sc, 7, ph);
                ring(lv, o, x, y + 0.7, z, 0.22 * sc, 5, ph);
                at(lv, o, x, y + 0.95, z, 0, 0, 0);
            }
            case "samurai" -> {
                ring(lv, o, x, y + 0.22, z, 0.32 * sc, 8, ph);
                for (int k = 0; k < 4; k++) { at(lv, o, x - 0.18 - k * 0.04, y + 0.3 + k * 0.1, z, 0, 0, 0); at(lv, o, x + 0.18 + k * 0.04, y + 0.3 + k * 0.1, z, 0, 0, 0); }
            }
            default -> {   // роги
                for (int k = 0; k < 4; k++) { at(lv, o, x - 0.2 - k * 0.04, y + 0.2 + k * 0.1, z, 0, 0, 0); at(lv, o, x + 0.2 + k * 0.04, y + 0.2 + k * 0.1, z, 0, 0, 0); }
            }
        }
    }

    private CosFx() { }
}
