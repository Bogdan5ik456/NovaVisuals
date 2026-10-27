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
            int s = 2, ox = w / 2 - (7 * s) / 2, oy = h / 2 - (7 * s) / 2;
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

        int y = 6;
        if (Settings.d.showFps) { mc.font.drawShadow(pose, "FPS: " + fps, 6, y, 0xFFB855FF); y += 11; }
        if (Settings.d.showClock) {
            String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            mc.font.drawShadow(pose, "Time: " + t, 6, y, 0xFFFFFFFF); y += 11;
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
