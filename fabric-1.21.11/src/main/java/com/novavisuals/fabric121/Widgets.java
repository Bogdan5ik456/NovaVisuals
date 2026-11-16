package com.novavisuals.fabric121;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Віджети HUD, які можна перетягувати: Info, Potions, Keybinds, Target, Armor, Markers, Friends, Radar.
 * Позиція кожного зберігається як частка екрана, тому не залежить від розміру вікна.
 */
public final class Widgets {
    private static final int CARD = 0xDD14141A, MUTED = 0xFF9A93B3, WHITE = 0xFFFFFFFF, GREEN = 0xFF55FF99, GRAY = 0xFF8A84A3;

    public abstract static class W {
        public final String id, title;
        final float dx, dy;

        W(String id, String title, float dx, float dy) { this.id = id; this.title = title; this.dx = dx; this.dy = dy; }

        public abstract boolean on();

        public abstract void setOn(boolean v);

        abstract int width(Minecraft mc, boolean edit);

        /** 0 = у звичайному режимі нічого малювати. */
        abstract int height(Minecraft mc, boolean edit);

        abstract void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit);
    }

    // ---------- загальні примітиви ----------
    public static int accent() { return Settings.THEMES[Math.max(0, Math.min(Settings.THEMES.length - 1, Settings.d.theme))][1]; }

    private static void card(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x55000000 | (accent() & 0xFFFFFF));
        g.fill(x, y, x + w, y + h, CARD);
    }

    private static int chip(GuiGraphics g, Minecraft mc, int x, int y, String text, int col) {
        int w = mc.font.width(text) + 8;
        g.fill(x, y, x + w, y + 12, CARD);
        g.fill(x, y + 11, x + w, y + 12, 0x66000000 | (accent() & 0xFFFFFF));
        g.drawString(mc.font, text, x + 4, y + 2, col);
        return w;
    }

    private static int chipW(Minecraft mc, String text) { return mc.font.width(text) + 8; }

    private static String time(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    // ---------- INFO: чипи зверху ліворуч (сервер, нік, FPS, пінг, час / координати, швидкість) ----------
    private static double bps;

    private static List<String[]> infoRow1(Minecraft mc) {
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer", "#E0A3FF"});
        if (mc.player != null) r.add(new String[]{mc.player.getName().getString(), "#FFFFFF"});
        if (Settings.d.showFps) r.add(new String[]{Hud.fps + " fps", "#B855FF"});
        if (mc.getConnection() != null && mc.player != null) {
            net.minecraft.client.multiplayer.PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (pi != null) r.add(new String[]{pi.getLatency() + " ms", "#55FF99"});
        }
        if (Settings.d.showClock) r.add(new String[]{java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), "#FFFFFF"});
        return r;
    }

    private static List<String[]> infoRow2(Minecraft mc) {
        List<String[]> r = new ArrayList<>();
        if (mc.player != null) {
            double v = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo) * 20.0;
            bps += (v - bps) * 0.08;
            if (Settings.d.showCoords) r.add(new String[]{"x " + Math.round(mc.player.getX()) + "  y " + Math.round(mc.player.getY()) + "  z " + Math.round(mc.player.getZ()), "#E0A3FF"});
            r.add(new String[]{String.format("%.1f bps", bps), "#FFFFFF"});
            if (Settings.d.showWorldTime && mc.level != null) r.add(new String[]{"день " + (mc.level.getGameTime() / 24000L + 1L), "#B9B2FF"});
        }
        return r;
    }

    private static int rowW(Minecraft mc, List<String[]> row) {
        int w = 0;
        for (String[] c : row) w += chipW(mc, c[0]) + 2;
        return Math.max(0, w - 2);
    }

    private static int argb(String hex) { return 0xFF000000 | Integer.parseInt(hex.substring(1), 16); }

    private static final W INFO = new W("info", "Info", 0.008f, 0.012f) {
        public boolean on() { return Settings.d.infoOn; }
        public void setOn(boolean v) { Settings.d.infoOn = v; }
        int width(Minecraft mc, boolean edit) { return Math.max(rowW(mc, infoRow1(mc)), rowW(mc, infoRow2(mc))); }
        int height(Minecraft mc, boolean edit) { return mc.player == null ? 0 : (infoRow2(mc).isEmpty() ? 12 : 26); }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            int cx = x;
            for (String[] c : infoRow1(mc)) cx += chip(g, mc, cx, y, c[0], argb(c[1])) + 2;
            cx = x;
            for (String[] c : infoRow2(mc)) cx += chip(g, mc, cx, y + 14, c[0], argb(c[1])) + 2;
        }
    };

    // ---------- POTIONS ----------
    private static final W POTIONS = new W("potions", "Potions", 0.008f, 0.17f) {
        public boolean on() { return Settings.d.potionsOn; }
        public void setOn(boolean v) { Settings.d.potionsOn = v; }
        private int count(Minecraft mc) { return mc.player == null ? 0 : mc.player.getActiveEffects().size(); }
        int width(Minecraft mc, boolean edit) { return 112; }
        int height(Minecraft mc, boolean edit) { int n = count(mc); return n == 0 ? (edit ? 38 : 0) : 14 + Math.min(n, 8) * 11 + 3; }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            int n = count(mc), h = height(mc, edit);
            card(g, x, y, 112, h);
            g.drawString(mc.font, "Potions", x + 4, y + 3, accent());
            int ry = y + 14;
            if (n == 0) {
                g.drawString(mc.font, "Сила II      01:23", x + 4, ry, 0xFFFF9E9E);
                g.drawString(mc.font, "Швидкість     00:45", x + 4, ry + 11, 0xFF9EE7FF);
                return;
            }
            int i = 0;
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                if (i++ >= 8) break;
                String name = Component.translatable(e.getDescriptionId()).getString();
                int amp = e.getAmplifier();
                if (amp > 0) name += " " + (amp < 10 ? new String[]{"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[amp] : String.valueOf(amp + 1));
                int col = Mth.hsvToRgb(Math.abs(e.getDescriptionId().hashCode() % 360) / 360f, 0.45f, 1f) | 0xFF000000;
                g.drawString(mc.font, mc.font.plainSubstrByWidth(name, 70), x + 4, ry, col);
                String t = e.getDuration() < 0 ? "inf" : time(e.getDuration() / 20L);
                g.drawString(mc.font, t, x + 108 - mc.font.width(t), ry, e.getDuration() >= 0 && e.getDuration() < 200 ? 0xFFFF6B8B : MUTED);
                ry += 11;
            }
        }
    };

    // ---------- KEYBINDS ----------
    private static final W KEYBINDS = new W("keybinds", "Keybinds", 0.008f, 0.42f) {
        public boolean on() { return Settings.d.keybindsOn; }
        public void setOn(boolean v) { Settings.d.keybindsOn = v; }
        private int rows() { return 2 + (Settings.d.animDance ? 1 : 0) + Math.min(Settings.d.binds.size(), 6); }
        int width(Minecraft mc, boolean edit) { return 112; }
        int height(Minecraft mc, boolean edit) { return 14 + rows() * 11 + 3; }
        private void row(GuiGraphics g, Minecraft mc, int x, int y, String label, int key, boolean held) {
            g.fill(x + 4, y + 3, x + 7, y + 6, held ? GREEN : 0xFF4A4560);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(label, 60), x + 10, y, held ? WHITE : MUTED);
            String k = Keys.name(key);
            g.drawString(mc.font, k, x + 108 - mc.font.width(k), y, held ? GREEN : accent());
        }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            card(g, x, y, 112, height(mc, edit));
            g.drawString(mc.font, "Keybinds", x + 4, y + 3, accent());
            int ry = y + 14;
            row(g, mc, x, ry, "Меню", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT, Keys.rightShift()); ry += 11;
            row(g, mc, x, ry, "Радар", org.lwjgl.glfw.GLFW.GLFW_KEY_V, Keys.down(org.lwjgl.glfw.GLFW.GLFW_KEY_V)); ry += 11;
            if (Settings.d.animDance) { row(g, mc, x, ry, "Танець", org.lwjgl.glfw.GLFW.GLFW_KEY_G, Keys.down(org.lwjgl.glfw.GLFW.GLFW_KEY_G)); ry += 11; }
            int i = 0;
            for (Settings.Bind b : Settings.d.binds) {
                if (i++ >= 6) break;
                row(g, mc, x, ry, b.cmd, b.key, Keys.down(b.key));
                ry += 11;
            }
        }
    };

    // ---------- TARGET ----------
    private static LivingEntity target(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) return le;
        LivingEntity last = NovaFabric121.lastHit;
        if (last != null && last.isAlive() && mc.level != null && mc.level.getGameTime() - NovaFabric121.lastHitTime < 100L) return last;
        return null;
    }

    private static final W TARGET = new W("target", "Target", 0.40f, 0.60f) {
        public boolean on() { return Settings.d.showHealth; }
        public void setOn(boolean v) { Settings.d.showHealth = v; }
        int width(Minecraft mc, boolean edit) { return 124; }
        int height(Minecraft mc, boolean edit) { return target(mc) != null || edit ? 30 : 0; }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            LivingEntity t = target(mc);
            String name = t != null ? t.getName().getString() : "Target_Player";
            float hp = t != null ? t.getHealth() : 14f, mhp = t != null ? t.getMaxHealth() : 20f;
            double dist = t != null && mc.player != null ? mc.player.distanceTo(t) : 4.2;
            card(g, x, y, 124, 30);
            Radar.badge(g, mc, name, x + 4, y + 7, accent());
            g.drawString(mc.font, mc.font.plainSubstrByWidth(name, 70), x + 24, y + 4, WHITE);
            String d = String.format("%.1fм", dist);
            g.drawString(mc.font, d, x + 120 - mc.font.width(d), y + 4, MUTED);
            float f = Math.max(0f, Math.min(1f, hp / Math.max(1f, mhp)));
            g.fill(x + 24, y + 17, x + 100, y + 22, 0xFF2C2745);
            g.fill(x + 24, y + 17, x + 24 + Math.round(76 * f), y + 22, f > 0.5f ? GREEN : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B);
            String s = String.valueOf(Math.round(hp));
            g.drawString(mc.font, s, x + 120 - mc.font.width(s), y + 16, WHITE);
        }
    };

    // ---------- ARMOR ----------
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_NAMES = {"Шолом", "Нагрудник", "Штани", "Черевики"};

    private static final W ARMOR = new W("armor", "Armor", 0.88f, 0.30f) {
        public boolean on() { return Settings.d.showArmor; }
        public void setOn(boolean v) { Settings.d.showArmor = v; }
        private int count(Minecraft mc) {
            int n = 0;
            if (mc.player != null) for (EquipmentSlot s : SLOTS) { ItemStack st = mc.player.getItemBySlot(s); if (!st.isEmpty() && st.isDamageableItem()) n++; }
            return n;
        }
        int width(Minecraft mc, boolean edit) { return 104; }
        int height(Minecraft mc, boolean edit) { int n = count(mc); return n == 0 ? (edit ? 38 : 0) : 14 + n * 11 + 3; }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            card(g, x, y, 104, height(mc, edit));
            g.drawString(mc.font, "Armor", x + 4, y + 3, accent());
            int ry = y + 14, n = count(mc);
            if (n == 0) { g.drawString(mc.font, "Шолом  87%", x + 4, ry, GREEN); g.drawString(mc.font, "Нагрудник  34%", x + 4, ry + 11, 0xFFFFD166); return; }
            for (int i = 0; i < SLOTS.length; i++) {
                ItemStack st = mc.player.getItemBySlot(SLOTS[i]);
                if (st.isEmpty() || !st.isDamageableItem()) continue;
                int pct = 100 - st.getDamageValue() * 100 / Math.max(1, st.getMaxDamage());
                int col = pct > 50 ? GREEN : pct > 20 ? 0xFFFFD166 : 0xFFFF6B8B;
                g.drawString(mc.font, SLOT_NAMES[i], x + 4, ry, MUTED);
                String t = pct + "%";
                g.drawString(mc.font, t, x + 100 - mc.font.width(t), ry, col);
                ry += 11;
            }
        }
    };

    // ---------- MARKERS ----------
    private static List<String> markerLines(Minecraft mc) {
        List<String> l = new ArrayList<>();
        if (mc.level == null || mc.player == null) return l;
        String dim = mc.level.dimension().toString();
        for (Settings.Marker m : Settings.d.markers) {
            if (!dim.equals(m.dim)) continue;
            double dx = m.x - mc.player.getX(), dz = m.z - mc.player.getZ(), dy = m.y - mc.player.getY();
            double rel = Mth.wrapDegrees(-Math.toDegrees(Math.atan2(dx, dz)) - mc.player.getYRot());
            String a = Math.abs(rel) <= 45 ? "^" : Math.abs(rel) >= 135 ? "v" : (rel > 0 ? ">" : "<");
            l.add("[" + a + "] " + m.name + "  " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "м");
            if (l.size() >= 8) break;
        }
        return l;
    }

    private static final W MARKERS = new W("markers", "Markers", 0.78f, 0.02f) {
        public boolean on() { return Settings.d.showMarkers; }
        public void setOn(boolean v) { Settings.d.showMarkers = v; }
        int width(Minecraft mc, boolean edit) { return 112; }
        int height(Minecraft mc, boolean edit) { int n = markerLines(mc).size(); return n == 0 ? (edit ? 38 : 0) : 14 + n * 11 + 3; }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            List<String> l = markerLines(mc);
            card(g, x, y, 112, height(mc, edit));
            g.drawString(mc.font, "Markers", x + 4, y + 3, accent());
            int ry = y + 14;
            if (l.isEmpty()) { g.drawString(mc.font, "[^] Дім  120м", x + 4, ry, WHITE); g.drawString(mc.font, "[>] Шахта  340м", x + 4, ry + 11, WHITE); return; }
            for (String s : l) { g.drawString(mc.font, mc.font.plainSubstrByWidth(s, 104), x + 4, ry, WHITE); ry += 11; }
        }
    };

    // ---------- FRIENDS ----------
    private static List<NovaNet.Friend> onlineFriends() {
        List<NovaNet.Friend> l = new ArrayList<>();
        for (NovaNet.Friend f : NovaNet.friends) if (f.online) l.add(f);
        return l;
    }

    private static final W FRIENDS = new W("friends", "Friends", 0.78f, 0.30f) {
        public boolean on() { return Settings.d.showFriends; }
        public void setOn(boolean v) { Settings.d.showFriends = v; }
        int width(Minecraft mc, boolean edit) { return 124; }
        int height(Minecraft mc, boolean edit) { int n = Math.min(6, onlineFriends().size()); return n == 0 ? (edit ? 38 : 0) : 14 + n * 11 + 3; }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            List<NovaNet.Friend> l = onlineFriends();
            card(g, x, y, 124, height(mc, edit));
            g.drawString(mc.font, "Friends", x + 4, y + 3, accent());
            int ry = y + 14;
            if (l.isEmpty()) { g.drawString(mc.font, "● Alex  у грі", x + 4, ry, GREEN); g.drawString(mc.font, "● Maria  в лаунчері", x + 4, ry + 11, GREEN); return; }
            int i = 0;
            for (NovaNet.Friend f : l) {
                if (i++ >= 6) break;
                String s = "● " + f.name + (f.inGame ? (f.server.isEmpty() ? "  у грі" : "  " + f.server) : "  лаунчер");
                g.drawString(mc.font, mc.font.plainSubstrByWidth(s, 116), x + 4, ry, GREEN);
                ry += 11;
            }
        }
    };

    // ---------- RADAR (список або карта; картки на екрані малює Hud) ----------
    private static final W RADAR = new W("radar", "Radar", 0.008f, 0.60f) {
        public boolean on() { return Settings.d.radarOn; }
        public void setOn(boolean v) { Settings.d.radarOn = v; }
        private boolean world() { return Settings.d.radarMode == 2; }
        int width(Minecraft mc, boolean edit) { return world() ? 90 : Settings.d.radarMode == 1 ? Radar.mapSize() : Radar.listW(); }
        int height(Minecraft mc, boolean edit) {
            if (world()) return edit ? 22 : 0;
            if (Settings.d.radarMode == 1) return Radar.mapSize();
            List<Radar.T> l = Radar.cur.isEmpty() && edit ? Radar.sample() : Radar.cur;
            return Radar.listH(l);
        }
        void draw(GuiGraphics g, Minecraft mc, int x, int y, boolean edit) {
            List<Radar.T> l = Radar.cur.isEmpty() && edit ? Radar.sample() : Radar.cur;
            if (world()) { card(g, x, y, 90, 22); g.drawString(mc.font, "Картки на екрані", x + 4, y + 7, MUTED); return; }
            if (Settings.d.radarMode == 1) Radar.map(g, mc, l, x, y); else Radar.list(g, mc, l, x, y);
        }
    };

    public static final List<W> ALL = List.of(INFO, POTIONS, KEYBINDS, RADAR, TARGET, ARMOR, MARKERS, FRIENDS);

    // ---------- позиції ----------
    /** {x, y, w, h} у координатах екрана GUI. */
    public static int[] rect(W w, Minecraft mc, boolean edit) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float[] p = Settings.d.hudPos.get(w.id);
        float fx = p != null && p.length >= 2 ? p[0] : w.dx, fy = p != null && p.length >= 2 ? p[1] : w.dy;
        int ww = w.width(mc, edit), hh = Math.max(1, w.height(mc, edit));
        int x = Math.max(0, Math.min(sw - ww, Math.round(fx * sw))), y = Math.max(0, Math.min(sh - hh, Math.round(fy * sh)));
        return new int[]{x, y, ww, hh};
    }

    public static void setPos(W w, Minecraft mc, int x, int y) {
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        Settings.d.hudPos.put(w.id, new float[]{x / (float) sw, y / (float) sh});
    }

    public static void resetPositions() { Settings.d.hudPos.clear(); Settings.save(); }

    private Widgets() { }
}
