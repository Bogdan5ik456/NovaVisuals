package com.novavisuals.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * «Гравці поруч»: хто де стоїть (сторона) і за скільки метрів.
 * Режими: 0 - список карток, 1 - карта-радар, 2 - картки прямо над гравцями на екрані.
 * Бачить лише гравців, яких сервер зараз показує тобі (завантажені чанки).
 */
public final class Radar {
    private static final int CARD = 0xDD14141A, GREEN = 0xFF55FF99, RED = 0xFFC4224B, MUTED = 0xFF9A93B3, WHITE = 0xFFFFFFFF;

    private static final class T {
        AbstractClientPlayer p; String name; double dist, dh, rel, relPitch; boolean friend; String world, side;
    }

    public static void render(PoseStack pose, float partial, int startY) {
        Minecraft mc = Minecraft.getInstance();
        Settings.Data d = Settings.d;
        if (!d.radarOn || mc.player == null || mc.level == null) return;
        double range = Settings.RADAR_RANGES[d.radarRange];
        List<T> list = new ArrayList<>();
        double ex = Mth.lerp(partial, mc.player.xo, mc.player.getX()), ey = mc.player.getEyeY(), ez = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            double dx = Mth.lerp(partial, p.xo, p.getX()) - ex, dz = Mth.lerp(partial, p.zo, p.getZ()) - ez;
            double dy = p.getY() + p.getBbHeight() * 1.05 - ey;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > range) continue;
            String name = p.getName().getString();
            boolean fr = NovaNet.isFriend(name);
            if (d.radarFriendsOnly && !fr) continue;
            T t = new T();
            t.p = p; t.name = name; t.dist = dist; t.friend = fr;
            t.dh = Math.sqrt(dx * dx + dz * dz);
            double target = -Math.toDegrees(Math.atan2(dx, dz));
            t.rel = Mth.wrapDegrees(target - yaw);
            t.relPitch = -Math.toDegrees(Math.atan2(dy, Math.max(0.001, t.dh))) - pitch;
            t.world = switch (Direction.fromYRot(target)) { case NORTH -> "Пн"; case SOUTH -> "Пд"; case EAST -> "Сх"; default -> "Зх"; };
            t.side = Math.abs(t.rel) <= 45 ? "спереду" : Math.abs(t.rel) >= 135 ? "ззаду" : t.rel > 0 ? "справа" : "зліва";
            list.add(t);
        }
        list.sort(Comparator.comparingDouble(a -> a.dist));
        int max = Settings.RADAR_MAX[d.radarMax];
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        switch (d.radarMode) {
            case 1 -> map(pose, mc, list, w, range, yaw);
            case 2 -> tags(pose, mc, list, w, h);
            default -> cards(pose, mc, list, startY);
        }
    }

    // ---------- режим 0: список (як на скріні з картками) ----------
    private static void cards(PoseStack p, Minecraft mc, List<T> list, int y0) {
        int x = 6, y = y0;
        for (T t : list) {
            int cw = 134, ch = 24;
            GuiComponent.fill(p, x, y, x + cw, y + ch, CARD);
            GuiComponent.fill(p, x, y, x + 2, y + ch, t.friend ? GREEN : Settings.THEMES[Settings.d.theme][1]);
            head(p, t.p, x + 5, y + 4, 16);
            mc.font.drawShadow(p, mc.font.plainSubstrByWidth(t.name, 56), x + 25, y + 3, t.friend ? GREEN : WHITE);
            if (Settings.d.radarHealth) {
                float f = Math.max(0f, Math.min(1f, t.p.getHealth() / Math.max(1f, t.p.getMaxHealth())));
                GuiComponent.fill(p, x + 25, y + 15, x + 79, y + 18, 0xFF2C2745);
                GuiComponent.fill(p, x + 25, y + 15, x + 25 + Math.round(54 * f), y + 18, f > 0.5f ? GREEN : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B);
                mc.font.draw(p, String.valueOf(Math.round(t.p.getHealth())), x + 82, y + 13, MUTED);
            }
            String dist = Math.round(t.dist) + "м";
            int bw = mc.font.width(dist) + 8;
            GuiComponent.fill(p, x + cw - bw - 6, y + 3, x + cw - 6, y + 13, RED);
            mc.font.drawShadow(p, dist, x + cw - bw - 2, y + 4, WHITE);
            arrow(p, x + cw - 20, y + 19, (float) t.rel, 0xFFFFFFFF);
            if (Settings.d.radarSide) mc.font.draw(p, t.world, x + cw - 13, y + 14, MUTED);
            y += ch + 3;
        }
    }

    // ---------- режим 1: карта-радар (ти в центрі, «вгору» = куди дивишся) ----------
    private static void map(PoseStack p, Minecraft mc, List<T> list, int w, double range, float yaw) {
        int size = 96, x = w - size - 8, y = 8, cx = x + size / 2, cy = y + size / 2, r = size / 2 - 5;
        int accent = Settings.THEMES[Settings.d.theme][1];
        GuiComponent.fill(p, x - 1, y - 1, x + size + 1, y + size + 1, accent);
        GuiComponent.fill(p, x, y, x + size, y + size, 0xCC0B0B0F);
        GuiComponent.fill(p, cx, y + 2, cx + 1, y + size - 2, 0x33FFFFFF);
        GuiComponent.fill(p, x + 2, cy, x + size - 2, cy + 1, 0x33FFFFFF);
        // мітка півночі
        double nrel = Math.toRadians(Mth.wrapDegrees(180.0 - yaw));
        mc.font.drawShadow(p, "N", (float) (cx + Math.sin(nrel) * (r + 1) - 2), (float) (cy - Math.cos(nrel) * (r + 1) - 3), 0xFFFF6B8B);
        arrow(p, cx, cy + 1, 0f, 0xFFFFFFFF);
        double scale = r / range;
        for (T t : list) {
            double a = Math.toRadians(t.rel), rr = Math.min(r, t.dh * scale);
            int px = (int) Math.round(cx + Math.sin(a) * rr), py = (int) Math.round(cy - Math.cos(a) * rr);
            int col = t.friend ? GREEN : accent;
            GuiComponent.fill(p, px - 2, py - 2, px + 3, py + 3, 0xFF0B0B0F);
            GuiComponent.fill(p, px - 1, py - 1, px + 2, py + 2, col);
            if (t.friend || list.size() <= 3) mc.font.drawShadow(p, mc.font.plainSubstrByWidth(t.name, 40), px + 4, py - 4, col);
        }
        String rt = Math.round(range) + "м";
        mc.font.draw(p, rt, x + size - mc.font.width(rt) - 3, y + size - 10, MUTED);
        if (list.isEmpty()) mc.font.draw(p, "—", cx - 2, y + size - 10, MUTED);
    }

    // ---------- режим 2: картки на екрані біля гравців ----------
    private static void tags(PoseStack p, Minecraft mc, List<T> list, int w, int h) {
        double fovV = Math.max(30.0, Math.min(110.0, mc.options.fov));
        double tanV = Math.tan(Math.toRadians(fovV / 2.0)), tanH = tanV * w / (double) h;
        for (T t : list) {
            int cw = 86, ch = 22;
            boolean front = Math.abs(t.rel) < 85;
            double sx, sy;
            boolean clamped = false;
            if (front) {
                sx = w / 2.0 + Math.tan(Math.toRadians(t.rel)) / tanH * (w / 2.0);
                sy = h / 2.0 + Math.tan(Math.toRadians(t.relPitch)) / tanV * (h / 2.0) - 14;
            } else {
                sx = w / 2.0 + Math.signum(t.rel) * (w / 2.0);
                sy = h * 0.72;
                clamped = true;
            }
            double nx = Math.max(cw / 2.0 + 6, Math.min(w - cw / 2.0 - 6, sx)), ny = Math.max(16, Math.min(h - 34, sy));
            if (nx != sx || ny != sy) clamped = true;
            int x = (int) nx - cw / 2, y = (int) ny - ch / 2;
            int accent = t.friend ? GREEN : Settings.THEMES[Settings.d.theme][1];
            GuiComponent.fill(p, x - 1, y - 1, x + cw + 1, y + ch + 1, 0x66000000 | (accent & 0xFFFFFF));
            GuiComponent.fill(p, x, y, x + cw, y + ch, CARD);
            head(p, t.p, x + 3, y + 3, 16);
            mc.font.drawShadow(p, mc.font.plainSubstrByWidth(t.name, 50), x + 22, y + 3, t.friend ? GREEN : WHITE);
            String dist = Math.round(t.dist) + "м" + (Settings.d.radarSide ? " " + t.world : "");
            mc.font.draw(p, dist, x + 22, y + 13, MUTED);
            if (Settings.d.radarHealth) {
                float f = Math.max(0f, Math.min(1f, t.p.getHealth() / Math.max(1f, t.p.getMaxHealth())));
                GuiComponent.fill(p, x + cw - 5, y + 3, x + cw - 3, y + ch - 3, 0xFF2C2745);
                int bh = Math.round((ch - 6) * f);
                GuiComponent.fill(p, x + cw - 5, y + ch - 3 - bh, x + cw - 3, y + ch - 3, f > 0.5f ? GREEN : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B);
            }
            if (clamped) arrow(p, x + (t.rel > 0 ? cw + 6 : -6), y + ch / 2, (float) (t.rel > 0 ? 90 : -90), accent);
        }
    }

    // ---------- малювання ----------
    private static void head(PoseStack p, AbstractClientPlayer pl, int x, int y, int sz) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, pl.getSkinTextureLocation());
        GuiComponent.blit(p, x, y, sz, sz, 8f, 8f, 8, 8, 64, 64);
        GuiComponent.blit(p, x, y, sz, sz, 40f, 8f, 8, 8, 64, 64);
    }

    /** Стрілка, що дивиться вгору при deg = 0 й повертається за годинниковою. */
    private static void arrow(PoseStack p, float cx, float cy, float deg, int col) {
        p.pushPose();
        p.translate(cx, cy, 0.0);
        p.mulPose(Vector3f.ZP.rotationDegrees(deg));
        GuiComponent.fill(p, 0, -5, 1, -4, col);
        GuiComponent.fill(p, -1, -4, 2, -3, col);
        GuiComponent.fill(p, -2, -3, 3, -2, col);
        GuiComponent.fill(p, 0, -2, 1, 3, col);
        p.popPose();
    }

    private Radar() { }
}
