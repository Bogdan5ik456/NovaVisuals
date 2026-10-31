package com.novavisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.ForgeIngameGui;

/** Приціл, FPS, координати та маркери на екрані. */
public final class Hud {
    private static int frames, fps;
    private static long last = System.nanoTime();

    public static void render(ForgeIngameGui gui, PoseStack pose, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        frames++;
        long now = System.nanoTime();
        if (now - last >= 1_000_000_000L) { fps = frames; frames = 0; last = now; }

        if (Settings.d.crosshair && mc.options.getCameraType().isFirstPerson()) {
            String[] p = Settings.STYLES[Settings.d.crosshairStyle];
            int col = Settings.COLORS[Settings.d.crosshairColor];
            int s = Math.max(1, Math.min(4, Settings.d.crosshairScale)), ox = w / 2 - (7 * s) / 2, oy = h / 2 - (7 * s) / 2;
            for (int r = 0; r < 7; r++)
                for (int c = 0; c < 7; c++)
                    if (p[r].charAt(c) == 'X')
                        GuiComponent.fill(pose, ox + c * s, oy + r * s, ox + c * s + s, oy + r * s + s, col);
        }

        if (Settings.d.cooldowns) {
            for (int i = 0; i < 9; i++) {
                net.minecraft.world.item.ItemStack st = mc.player.getInventory().getItem(i);
                if (st.isEmpty()) continue;
                float cd = mc.player.getCooldowns().getCooldownPercent(st.getItem(), partial);
                if (cd <= 0f) continue;
                int bx = w / 2 - 88 + i * 20, by = h - 26;
                GuiComponent.fill(pose, bx, by, bx + 16, by + 3, 0xCC0B0B0F);
                GuiComponent.fill(pose, bx, by, bx + Math.max(1, Math.round(16 * cd)), by + 3, 0xFFB855FF);
            }
        }

        if (Settings.d.showArmor) {   // броня й міцність справа
            for (int i = 0; i < 4; i++) {
                net.minecraft.world.item.ItemStack st = mc.player.getInventory().armor.get(3 - i);
                if (st.isEmpty()) continue;
                int ax = w - 24, ay = h / 2 - 44 + i * 20;
                mc.getItemRenderer().renderGuiItem(st, ax, ay);
                mc.getItemRenderer().renderGuiItemDecorations(mc.font, st, ax, ay);
                if (st.isDamageableItem()) {
                    int pct = 100 - st.getDamageValue() * 100 / st.getMaxDamage();
                    String t = pct + "%";
                    mc.font.drawShadow(pose, t, ax - mc.font.width(t) - 3, ay + 4, pct > 50 ? 0xFF55FF99 : pct > 20 ? 0xFFFFD166 : 0xFFFF6B8B);
                }
            }
        }

        if (Settings.d.showHealth) {   // здоров'я: твоє й цілі під прицілом
            float hp = mc.player.getHealth(), mhp = mc.player.getMaxHealth();
            String t = Math.round(hp) + " / " + Math.round(mhp);
            mc.font.drawShadow(pose, t, w / 2f - mc.font.width(t) / 2f, h / 2f + 14, hp <= mhp * 0.3f ? 0xFFFF6B8B : 0xFFFFFFFF);
            if (mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity le) {
                float f = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
                int bw = 50, bx = w / 2 - bw / 2, by = h / 2 + 26;
                GuiComponent.fill(pose, bx - 1, by - 1, bx + bw + 1, by + 5, 0xCC0B0B0F);
                GuiComponent.fill(pose, bx, by, bx + Math.round(bw * f), by + 4, f > 0.5f ? 0xFF55FF99 : f > 0.25f ? 0xFFFFD166 : 0xFFFF6B8B);
                String nm = le.getName().getString() + " " + Math.round(le.getHealth());
                mc.font.drawShadow(pose, nm, w / 2f - mc.font.width(nm) / 2f, by + 8, 0xFFE0A3FF);
            }
        }

        int y = 6;
        if (Settings.d.showFps) { mc.font.drawShadow(pose, "FPS: " + fps, 6, y, 0xFFB855FF); y += 11; }
        if (Settings.d.showClock) {
            String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            mc.font.drawShadow(pose, "Time: " + t, 6, y, 0xFFFFFFFF); y += 11;
        }
        if (Settings.d.showWorldTime && mc.level != null) {
            long dt = mc.level.getDayTime();
            long tod = ((dt % 24000L) + 24000L) % 24000L;
            int hh = (int) ((tod / 1000L + 6L) % 24L), mm = (int) ((tod % 1000L) * 60L / 1000L);
            mc.font.drawShadow(pose, String.format("Світ: %02d:%02d (день %d)", hh, mm, dt / 24000L + 1L), 6, y, 0xFFB9B2FF);
            y += 11;
        }
        if (Settings.d.showFriends && !Settings.d.friends.isEmpty() && mc.getConnection() != null) {
            java.util.List<String> on = new java.util.ArrayList<>();
            for (net.minecraft.client.multiplayer.PlayerInfo pi : mc.getConnection().getOnlinePlayers()) {
                String n = pi.getProfile().getName();
                for (String f : Settings.d.friends) if (f.equalsIgnoreCase(n)) on.add(n);
            }
            if (!on.isEmpty()) { mc.font.drawShadow(pose, "Друзі онлайн: " + String.join(", ", on), 6, y, 0xFF55FF99); y += 11; }
        }
        if (Settings.d.showCoords) {
            String t = String.format("XYZ: %.0f / %.0f / %.0f  (%s)", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getDirection().getName());
            mc.font.drawShadow(pose, t, 6, y, 0xFFE0A3FF); y += 11;
        }
        if (Settings.d.showMarkers && mc.level != null) {
            String dim = mc.level.dimension().location().toString();
            for (Settings.Marker m : Settings.d.markers) {
                if (!dim.equals(m.dim)) continue;
                double dx = m.x - mc.player.getX(), dz = m.z - mc.player.getZ(), dy = m.y - mc.player.getY();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double target = -Math.toDegrees(Math.atan2(dx, dz));
                double rel = Mth.wrapDegrees(target - mc.player.getYRot());
                String arrow = Math.abs(rel) <= 45 ? "^" : Math.abs(rel) >= 135 ? "v" : (rel > 0 ? ">" : "<");
                mc.font.drawShadow(pose, "[" + arrow + "] " + m.name + "  " + Math.round(dist) + "m", 6, y, 0xFFFFFFFF);
                y += 11;
            }
        }
    }

    private Hud() { }
}
