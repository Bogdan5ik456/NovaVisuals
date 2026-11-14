package com.novavisuals.fabric121;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** FPS, координати, здоров'я та міцність броні на екрані. */
public final class Hud {
    private static int frames, fps;
    private static long last = System.nanoTime();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Шолом", "Нагрудник", "Штани", "Черевики"};

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        frames++;
        long now = System.nanoTime();
        if (now - last >= 1_000_000_000L) { fps = frames; frames = 0; last = now; }

        int y = 6;
        if (Settings.d.fps) { g.drawString(mc.font, "FPS: " + fps, 6, y, 0xFFB855FF); y += 11; }
        if (Settings.d.coords) {
            String t = String.format("XYZ: %.0f / %.0f / %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            g.drawString(mc.font, t, 6, y, 0xFFE0A3FF);
            y += 11;
        }
        if (Settings.d.health) {
            String t = "HP: " + Math.round(mc.player.getHealth()) + " / " + Math.round(mc.player.getMaxHealth());
            g.drawString(mc.font, t, 6, y, 0xFFFF8FA3);
            y += 11;
        }
        if (Settings.d.armor) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack st = mc.player.getItemBySlot(ARMOR[i]);
                if (st.isEmpty() || !st.isDamageableItem()) continue;
                int pct = 100 - st.getDamageValue() * 100 / st.getMaxDamage();
                g.drawString(mc.font, ARMOR_NAMES[i] + ": " + pct + "%", 6, y, pct > 50 ? 0xFF55FF99 : pct > 20 ? 0xFFFFD166 : 0xFFFF6B8B);
                y += 11;
            }
        }
        Radar.render(g, tracker.getGameTimeDeltaPartialTick(true), y + 4);
    }

    private Hud() { }
}
