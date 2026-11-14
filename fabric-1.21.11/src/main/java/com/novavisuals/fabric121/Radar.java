package com.novavisuals.fabric121;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** «Гравці поруч» для 1.21.11: список карток, карта-радар, картки на екрані. Сторона й відстань у метрах. */
public final class Radar {
    private static final int CARD = 0xDD14141A, GREEN = 0xFF55FF99, RED = 0xFFC4224B, MUTED = 0xFF9A93B3, WHITE = 0xFFFFFFFF, NEON = 0xFFB855FF;

    private static final class T {
        AbstractClientPlayer p; String name, world; double dist, dh, rel, relPitch; boolean friend;
    }

    public static void render(GuiGraphics g, float partial, int startY) {
        Minecraft mc = Minecraft.getInstance();
        Settings.Data d = Settings.d;
        if (!d.radarOn || mc.player == null || mc.level == null) return;
        double range = Settings.RADAR_RANGES[d.radarRange];
        double ex = Mth.lerp(partial, mc.player.xo, mc.player.getX()), ey = mc.player.getEyeY(), ez = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        List<T> list = new ArrayList<>();
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
            Direction dir = Direction.fromYRot(target);
            t.world = dir == Direction.NORTH ? "Пн" : dir == Direction.SOUTH ? "Пд" : dir == Direction.EAST ? "Сх" : "Зх";
            list.add(t);
        }
        list.sort(Comparator.comparingDouble(a -> a.dist));
        int max = Settings.RADAR_MAX[d.radarMax];
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        switch (d.radarMode) {
            case 1 -> map(g, mc, list, w, range, yaw);
            case 2 -> tags(g, mc, list, w, h);
            default -> cards(g, mc, list, startY);
        }
    }

    private static String arrow(double rel) {
        double a = Math.abs(rel);
        return a <= 22.5 ? "^" : a >= 157.5 ? "v" : a <= 67.5 ? (rel > 0 ? "^>" : "<^") : a >= 112.5 ? (rel > 0 ? "v>" : "<v") : (rel > 0 ? ">" : "<");
    }

    private static void badge(GuiGraphics g, Minecraft mc, String name, int x, int y, int col) {
        g.fill(x, y, x + 16, y + 16, 0xFF2C2745);
        String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        g.drawString(mc.font, ch, x + 8 - mc.font.width(ch) / 2, y + 4, col);
    }

    private static void cards(GuiGraphics g, Minecraft mc, List<T> list, int y0) {
        int x = 6, y = y0;
        for (T t : list) {
            int cw = 134, ch = 24;
            g.fill(x, y, x + cw, y + ch, CARD);
            g.fill(x, y, x + 2, y + ch, t.friend ? GREEN : NEON);
            badge(g, mc, t.name, x + 5, y + 4, t.friend ? GREEN : WHITE);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 56), x + 25, y + 3, t.friend ? GREEN : WHITE);
            float f = Math.max(0f, Math.min(1f, t.p.getHealth() / Math.max(1f, t.p.getMaxHealth())));
            g.fill(x + 25, y + 15, x + 79, y + 18, 0xFF2C2745);
            g.fill(x + 25, y + 15, x + 25 + Math.round(54 * f), y + 18, f > 0.5f ? GREEN : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B);
            String dist = Math.round(t.dist) + "м";
            int bw = mc.font.width(dist) + 8;
            g.fill(x + cw - bw - 6, y + 3, x + cw - 6, y + 13, RED);
            g.drawString(mc.font, dist, x + cw - bw - 2, y + 4, WHITE);
            g.drawString(mc.font, arrow(t.rel) + " " + t.world, x + cw - 36, y + 14, MUTED);
            y += ch + 3;
        }
    }

    private static void map(GuiGraphics g, Minecraft mc, List<T> list, int w, double range, float yaw) {
        int size = 96, x = w - size - 8, y = 8, cx = x + size / 2, cy = y + size / 2, r = size / 2 - 5;
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, NEON);
        g.fill(x, y, x + size, y + size, 0xCC0B0B0F);
        g.fill(cx, y + 2, cx + 1, y + size - 2, 0x33FFFFFF);
        g.fill(x + 2, cy, x + size - 2, cy + 1, 0x33FFFFFF);
        double nrel = Math.toRadians(Mth.wrapDegrees(180.0 - yaw));
        g.drawString(mc.font, "N", (int) (cx + Math.sin(nrel) * (r + 1) - 2), (int) (cy - Math.cos(nrel) * (r + 1) - 3), 0xFFFF6B8B);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, WHITE);
        double scale = r / range;
        for (T t : list) {
            double a = Math.toRadians(t.rel), rr = Math.min(r, t.dh * scale);
            int px = (int) Math.round(cx + Math.sin(a) * rr), py = (int) Math.round(cy - Math.cos(a) * rr);
            int col = t.friend ? GREEN : NEON;
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF0B0B0F);
            g.fill(px - 1, py - 1, px + 2, py + 2, col);
            if (t.friend || list.size() <= 3) g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 40), px + 4, py - 4, col);
        }
        String rt = Math.round(range) + "м";
        g.drawString(mc.font, rt, x + size - mc.font.width(rt) - 3, y + size - 10, MUTED);
    }

    private static void tags(GuiGraphics g, Minecraft mc, List<T> list, int w, int h) {
        double fovV = Math.max(30.0, Math.min(110.0, mc.options.fov().get()));
        double tanV = Math.tan(Math.toRadians(fovV / 2.0)), tanH = tanV * w / (double) h;
        for (T t : list) {
            int cw = 86, ch = 22;
            double sx, sy;
            if (Math.abs(t.rel) < 85) {
                sx = w / 2.0 + Math.tan(Math.toRadians(t.rel)) / tanH * (w / 2.0);
                sy = h / 2.0 + Math.tan(Math.toRadians(t.relPitch)) / tanV * (h / 2.0) - 14;
            } else { sx = w / 2.0 + Math.signum(t.rel) * (w / 2.0); sy = h * 0.72; }
            double nx = Math.max(cw / 2.0 + 6, Math.min(w - cw / 2.0 - 6, sx)), ny = Math.max(16, Math.min(h - 34, sy));
            int x = (int) nx - cw / 2, y = (int) ny - ch / 2;
            int accent = t.friend ? GREEN : NEON;
            g.fill(x - 1, y - 1, x + cw + 1, y + ch + 1, 0x66000000 | (accent & 0xFFFFFF));
            g.fill(x, y, x + cw, y + ch, CARD);
            badge(g, mc, t.name, x + 3, y + 3, accent);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 50), x + 22, y + 3, t.friend ? GREEN : WHITE);
            g.drawString(mc.font, Math.round(t.dist) + "м " + t.world + " " + arrow(t.rel), x + 22, y + 13, MUTED);
        }
    }

    private Radar() { }
}
