package com.novavisuals.fabric121;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Режим редагування HUD: меню зникає, лишаються тільки віджети. Тягни їх мишею (ліва кнопка),
 * права кнопка вмикає/вимикає віджет. «Готово» повертає в меню.
 * Миша читається напряму з GLFW, тож екран не залежить від змін у вхідному API Minecraft.
 */
public class HudEditScreen extends Screen {
    private final int returnTab;
    private Widgets.W drag;
    private int offX, offY;
    private boolean leftPrev = true, rightPrev = true;   // кнопка ще може бути затиснута після кліку в меню
    private Button done, reset;

    public HudEditScreen(int returnTab) {
        super(Component.literal("HUD"));
        this.returnTab = returnTab;
    }

    @Override
    protected void init() {
        done = Button.builder(Component.literal("Готово"), b -> onClose()).bounds(width / 2 - 104, 8, 100, 20).build();
        reset = Button.builder(Component.literal("Скинути позиції"), b -> Widgets.resetPositions()).bounds(width / 2 + 4, 8, 100, 20).build();
        addRenderableWidget(done);
        addRenderableWidget(reset);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Settings.save();
        Minecraft.getInstance().setScreen(new MenuScreen(returnTab));
    }

    /** Свій легкий фон замість розмиття: світ лишається видно. */
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, width, height, 0x44000000);
        Radar.update(mc, pt);
        boolean left = Keys.mouse(0), right = Keys.mouse(1);
        boolean overButtons = done.isMouseOver(mx, my) || reset.isMouseOver(mx, my);
        int acc = Widgets.accent();

        for (Widgets.W w : Widgets.ALL) {
            int[] r = Widgets.rect(w, mc, true);
            w.draw(g, mc, r[0], r[1], true);
            int col = drag == w ? 0xFF55FF99 : w.on() ? acc : 0xFFFF6B8B;
            g.fill(r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] - 1, col);
            g.fill(r[0] - 2, r[1] + r[3] + 1, r[0] + r[2] + 2, r[1] + r[3] + 2, col);
            g.fill(r[0] - 2, r[1] - 2, r[0] - 1, r[1] + r[3] + 2, col);
            g.fill(r[0] + r[2] + 1, r[1] - 2, r[0] + r[2] + 2, r[1] + r[3] + 2, col);
            if (!w.on()) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x99000000);
                g.drawString(mc.font, "ВИМК.", r[0] + 4, r[1] + r[3] / 2 - 4, 0xFFFF6B8B);
            }
            g.drawString(mc.font, w.title, r[0], r[1] - 11, col);
        }

        // перетягування
        if (left && !leftPrev && !overButtons) {
            for (int i = Widgets.ALL.size() - 1; i >= 0; i--) {
                Widgets.W w = Widgets.ALL.get(i);
                int[] r = Widgets.rect(w, mc, true);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) { drag = w; offX = mx - r[0]; offY = my - r[1]; break; }
            }
        }
        if (drag != null && left) {
            int[] r = Widgets.rect(drag, mc, true);
            int nx = Math.max(0, Math.min(width - r[2], ((mx - offX) / 2) * 2));
            int ny = Math.max(0, Math.min(height - r[3], ((my - offY) / 2) * 2));
            Widgets.setPos(drag, mc, nx, ny);
        }
        if (!left && drag != null) { drag = null; Settings.save(); }

        // права кнопка: вмикає/вимикає віджет під мишею
        if (right && !rightPrev && !overButtons) {
            for (int i = Widgets.ALL.size() - 1; i >= 0; i--) {
                Widgets.W w = Widgets.ALL.get(i);
                int[] r = Widgets.rect(w, mc, true);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) { w.setOn(!w.on()); Settings.save(); break; }
            }
        }
        leftPrev = left;
        rightPrev = right;

        done.render(g, mx, my, pt);
        reset.render(g, mx, my, pt);
        String hint = "Тягни віджети мишею  ·  ПКМ: вимкнути / увімкнути  ·  Готово: назад у меню";
        g.drawString(mc.font, hint, width / 2 - mc.font.width(hint) / 2, 34, 0xFFE0A3FF);
    }
}
