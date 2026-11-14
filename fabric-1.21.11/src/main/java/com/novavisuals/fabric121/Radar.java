package com.novavisuals.fabric121;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Гравці поруч»: сторона, відстань у метрах, координати. Режими: список, карта-радар, картки на екрані.
 * ПАМ'ЯТЬ: коли гравець зникає з поля зору (відійшов далеко), його остання позиція лишається на радарі
 * (тьмяно, з підписом «скільки тому»), поки не мине час пам'яті або він не вийде з сервера.
 */
public final class Radar {
    private static final int CARD = 0xDD14141A, GREEN = 0xFF55FF99, RED = 0xFFC4224B, MUTED = 0xFF9A93B3, WHITE = 0xFFFFFFFF;

    public static final class T {
        public String name = "", world = "Пн";
        public double dist, dh, rel, relPitch;
        public double x, y, z;
        public float hp = 20f, mhp = 20f;
        public boolean friend, remembered;
        public long age;   // мс від останнього разу, коли бачили
    }

    private static final class Seen { String name, dim; double x, y, z; float hp, mhp; long ts; }
    private static final Map<UUID, Seen> seen = new HashMap<>();

    /** Список для поточного кадру (оновлюється з Hud). */
    public static List<T> cur = new ArrayList<>();

    public static void clear() { seen.clear(); cur = new ArrayList<>(); }

    private static T make(Minecraft mc, double ex, double ey, double ez, float yaw, float pitch, double x, double y, double z) {
        T t = new T();
        double dx = x - ex, dz = z - ez, dy = y - ey;
        t.x = x; t.y = y; t.z = z;
        t.dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        t.dh = Math.sqrt(dx * dx + dz * dz);
        double target = -Math.toDegrees(Math.atan2(dx, dz));
        t.rel = Mth.wrapDegrees(target - yaw);
        t.relPitch = -Math.toDegrees(Math.atan2(dy, Math.max(0.001, t.dh))) - pitch;
        Direction dir = Direction.fromYRot(target);
        t.world = dir == Direction.NORTH ? "Пн" : dir == Direction.SOUTH ? "Пд" : dir == Direction.EAST ? "Сх" : "Зх";
        return t;
    }

    public static void update(Minecraft mc, float partial) {
        Settings.Data d = Settings.d;
        List<T> list = new ArrayList<>();
        if (!d.radarOn || mc.player == null || mc.level == null) { cur = list; return; }
        long now = System.currentTimeMillis();
        long ttl = Settings.MEMORY_MS[Math.max(0, Math.min(Settings.MEMORY_MS.length - 1, d.radarMemory))];
        double range = Settings.RADAR_RANGES[d.radarRange];
        String dim = mc.level.dimension().toString();
        double ex = Mth.lerp(partial, mc.player.xo, mc.player.getX()), ey = mc.player.getEyeY(), ez = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        Set<UUID> vis = new HashSet<>();

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || p.isSpectator()) continue;
            vis.add(p.getUUID());
            double px = Mth.lerp(partial, p.xo, p.getX()), pz = Mth.lerp(partial, p.zo, p.getZ());
            String name = p.getName().getString();
            if (ttl > 0) {
                Seen s = seen.computeIfAbsent(p.getUUID(), k -> new Seen());
                s.name = name; s.dim = dim; s.x = px; s.y = p.getY(); s.z = pz; s.hp = p.getHealth(); s.mhp = p.getMaxHealth(); s.ts = now;
            }
            T t = make(mc, ex, ey, ez, yaw, pitch, px, p.getY() + p.getBbHeight() * 1.05, pz);
            if (t.dist > range) continue;
            t.name = name; t.hp = p.getHealth(); t.mhp = p.getMaxHealth();
            t.friend = NovaNet.isFriend(name);
            if (d.radarFriendsOnly && !t.friend) continue;
            list.add(t);
        }

        for (Iterator<Map.Entry<UUID, Seen>> it = seen.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Seen> en = it.next();
            Seen s = en.getValue();
            if (vis.contains(en.getKey())) continue;
            boolean online = mc.getConnection() != null && mc.getConnection().getPlayerInfo(en.getKey()) != null;
            if (ttl <= 0 || !online || (ttl != Long.MAX_VALUE && now - s.ts > ttl)) { it.remove(); continue; }
            if (!dim.equals(s.dim)) continue;   // в іншому вимірі: не показуємо, але пам'ятаємо
            T t = make(mc, ex, ey, ez, yaw, pitch, s.x, s.y + 1.6, s.z);
            if (t.dist > 3000) continue;
            t.name = s.name; t.hp = s.hp; t.mhp = s.mhp; t.remembered = true; t.age = now - s.ts;
            t.friend = NovaNet.isFriend(s.name);
            if (d.radarFriendsOnly && !t.friend) continue;
            list.add(t);
        }

        list.sort(Comparator.<T>comparingInt(a -> a.remembered ? 1 : 0).thenComparingDouble(a -> a.dist));
        int max = Settings.RADAR_MAX[d.radarMax];
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
        cur = list;
    }

    // ---------- допоміжне ----------
    private static int accent() { return Settings.THEMES[Math.max(0, Math.min(Settings.THEMES.length - 1, Settings.d.theme))][1]; }

    public static String ageText(long ms) {
        long s = ms / 1000L;
        return s < 60 ? s + "с" : s < 3600 ? (s / 60) + "хв" : (s / 3600) + "г";
    }

    private static String arrow(double rel) {
        double a = Math.abs(rel);
        return a <= 22.5 ? "^" : a >= 157.5 ? "v" : a <= 67.5 ? (rel > 0 ? "^>" : "<^") : a >= 112.5 ? (rel > 0 ? "v>" : "<v") : (rel > 0 ? ">" : "<");
    }

    private static int fade(int col, boolean dim) {
        if (!dim) return col;
        int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
        return 0xFF000000 | ((r * 6 / 10) << 16) | ((g * 6 / 10) << 8) | (b * 6 / 10);
    }

    public static void badge(GuiGraphics g, Minecraft mc, String name, int x, int y, int col) {
        g.fill(x, y, x + 16, y + 16, 0xFF2C2745);
        String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        g.drawString(mc.font, ch, x + 8 - mc.font.width(ch) / 2, y + 4, col);
    }

    public static List<T> sample() {
        List<T> l = new ArrayList<>();
        T a = new T(); a.name = "Player_1"; a.dist = 12; a.rel = 40; a.hp = 20; a.x = 120; a.y = 64; a.z = -35; l.add(a);
        T b = new T(); b.name = "Friend_2"; b.dist = 87; b.rel = -120; b.hp = 9; b.friend = true; b.x = -40; b.y = 70; b.z = 200; b.world = "Пд"; l.add(b);
        T c = new T(); c.name = "Seen_3"; c.dist = 310; c.rel = 170; c.hp = 14; c.remembered = true; c.age = 95_000; c.world = "Сх"; c.x = 500; c.y = 66; c.z = 12; l.add(c);
        return l;
    }

    // ---------- режим 0: список карток ----------
    private static int cardH() { return Settings.d.radarCoords ? 34 : 24; }

    public static int listW() { return 134; }

    public static int listH(List<T> l) { return l.isEmpty() ? 0 : l.size() * (cardH() + 3) - 3; }

    public static void list(GuiGraphics g, Minecraft mc, List<T> list, int x, int y) {
        int ch = cardH(), cw = 134;
        for (T t : list) {
            boolean dim = t.remembered;
            int acc = fade(t.friend ? GREEN : accent(), dim);
            g.fill(x, y, x + cw, y + ch, CARD);
            g.fill(x, y, x + 2, y + ch, acc);
            badge(g, mc, t.name, x + 5, y + 4, fade(t.friend ? GREEN : WHITE, dim));
            g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 56), x + 25, y + 3, fade(t.friend ? GREEN : WHITE, dim));
            if (Settings.d.radarHealth) {
                float f = Math.max(0f, Math.min(1f, t.hp / Math.max(1f, t.mhp)));
                g.fill(x + 25, y + 14, x + 79, y + 17, 0xFF2C2745);
                g.fill(x + 25, y + 14, x + 25 + Math.round(54 * f), y + 17, fade(f > 0.5f ? GREEN : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B, dim));
                g.drawString(mc.font, String.valueOf(Math.round(t.hp)), x + 82, y + 12, MUTED);
            }
            if (Settings.d.radarCoords) g.drawString(mc.font, Math.round(t.x) + " " + Math.round(t.y) + " " + Math.round(t.z), x + 25, y + 23, MUTED);
            String dist = (dim ? "~" : "") + Math.round(t.dist) + "м";
            int bw = mc.font.width(dist) + 8;
            g.fill(x + cw - bw - 6, y + 3, x + cw - 6, y + 13, dim ? 0xFF6B2A3C : RED);
            g.drawString(mc.font, dist, x + cw - bw - 2, y + 4, WHITE);
            String side = arrow(t.rel) + (Settings.d.radarSide ? " " + t.world : "");
            g.drawString(mc.font, side, x + cw - mc.font.width(side) - 6, y + 14, MUTED);
            if (dim) g.drawString(mc.font, ageText(t.age), x + cw - mc.font.width(ageText(t.age)) - 6, y + ch - 10, 0xFFFFD166);
            y += ch + 3;
        }
    }

    // ---------- режим 1: карта-радар ----------
    public static int mapSize() { return 96; }

    public static void map(GuiGraphics g, Minecraft mc, List<T> list, int x, int y) {
        int size = 96, cx = x + size / 2, cy = y + size / 2, r = size / 2 - 5;
        double range = Settings.RADAR_RANGES[Settings.d.radarRange];
        float yaw = mc.player != null ? mc.player.getYRot() : 0f;
        int acc = accent();
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, acc);
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
            int col = fade(t.friend ? GREEN : acc, t.remembered);
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF0B0B0F);
            g.fill(px - 1, py - 1, px + 2, py + 2, col);
            if (t.friend || list.size() <= 3) g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 40), px + 4, py - 4, col);
        }
        String rt = Math.round(range) + "м";
        g.drawString(mc.font, rt, x + size - mc.font.width(rt) - 3, y + size - 10, MUTED);
    }

    // ---------- режим 2: картки на екрані біля гравців ----------
    public static void tags(GuiGraphics g, Minecraft mc, List<T> list, int w, int h) {
        double fovV = Math.max(30.0, Math.min(110.0, mc.options.fov().get()));
        double tanV = Math.tan(Math.toRadians(fovV / 2.0)), tanH = tanV * w / (double) h;
        for (T t : list) {
            int cw = 90, ch = 22;
            double sx, sy;
            if (Math.abs(t.rel) < 85) {
                sx = w / 2.0 + Math.tan(Math.toRadians(t.rel)) / tanH * (w / 2.0);
                sy = h / 2.0 + Math.tan(Math.toRadians(t.relPitch)) / tanV * (h / 2.0) - 14;
            } else { sx = w / 2.0 + Math.signum(t.rel) * (w / 2.0); sy = h * 0.72; }
            double nx = Math.max(cw / 2.0 + 6, Math.min(w - cw / 2.0 - 6, sx)), ny = Math.max(16, Math.min(h - 34, sy));
            int x = (int) nx - cw / 2, y = (int) ny - ch / 2;
            int acc = fade(t.friend ? GREEN : accent(), t.remembered);
            g.fill(x - 1, y - 1, x + cw + 1, y + ch + 1, 0x66000000 | (acc & 0xFFFFFF));
            g.fill(x, y, x + cw, y + ch, CARD);
            badge(g, mc, t.name, x + 3, y + 3, acc);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(t.name, 54), x + 22, y + 3, fade(t.friend ? GREEN : WHITE, t.remembered));
            String info = (t.remembered ? "~" : "") + Math.round(t.dist) + "м " + t.world + " " + arrow(t.rel);
            g.drawString(mc.font, info, x + 22, y + 13, MUTED);
            if (t.remembered) g.drawString(mc.font, ageText(t.age), x + cw - mc.font.width(ageText(t.age)) - 3, y + 3, 0xFFFFD166);
        }
    }

    private Radar() { }
}
