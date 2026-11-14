package com.novavisuals.fabric121;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Малює всі віджети HUD на їхніх (перетягнутих) місцях і картки гравців на екрані. */
public final class Hud {
    public static int frames, fps;
    private static long last = System.nanoTime();

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        frames++;
        long now = System.nanoTime();
        if (now - last >= 1_000_000_000L) { fps = frames; frames = 0; last = now; }
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (mc.screen instanceof HudEditScreen) return;   // у режимі редагування малює сам екран

        float partial = tracker.getGameTimeDeltaPartialTick(true);
        Radar.update(mc, partial);
        for (Widgets.W w : Widgets.ALL) {
            if (!w.on() || w.height(mc, false) <= 0) continue;
            int[] r = Widgets.rect(w, mc, false);
            w.draw(g, mc, r[0], r[1], false);
        }
        if (Settings.d.radarOn && Settings.d.radarMode == 2)
            Radar.tags(g, mc, Radar.cur, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private Hud() { }
}
