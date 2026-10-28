package com.novavisuals;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.TextComponent;

import java.util.Random;

/** Головний екран Nova Visuals: зоряне тло й список пунктів. Мови: українська та English. */
public class NovaTitleScreen extends Screen {
    private static final String[][] TXT = {
        {"Одиночна гра", "Мережева гра", "Візуали", "Налаштування", "Вихід"},
        {"Singleplayer", "Multiplayer", "Visuals", "Options", "Quit"}};
    private static final int BW = 200, BH = 24, GAP = 8;

    private final long start = System.currentTimeMillis();
    private float[] sx, sy, sp;
    private final float[] hov = new float[5];
    private int top, left;

    public NovaTitleScreen() { super(new TextComponent("Nova Visuals")); }

    @Override
    protected void init() {
        Random r = new Random(42);
        int n = 140;
        sx = new float[n]; sy = new float[n]; sp = new float[n];
        for (int i = 0; i < n; i++) { sx[i] = r.nextFloat() * width; sy[i] = r.nextFloat() * height; sp[i] = 0.5f + r.nextFloat() * 2f; }
        left = (width - BW) / 2;
        top = height / 2 - 10;
    }

    private int lang() { return "en".equals(Settings.d.lang) ? 1 : 0; }

    private void blob(PoseStack p, int cx, int cy, int radius, int rgb) {
        for (int layer = 0; layer < 5; layer++) {
            int r = radius - layer * radius / 6;
            for (int dy = -r; dy <= r; dy += 4) {
                int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
                GuiComponent.fill(p, cx - hw, cy + dy, cx + hw, cy + dy + 4, (9 << 24) | rgb);
            }
        }
    }

    @Override
    public void render(PoseStack p, int mx, int my, float pt) {
        int[] theme = Settings.THEMES[Settings.d.theme];
        float t = (System.currentTimeMillis() - start) / 1000f;

        GuiComponent.fillGradient(p, 0, 0, width, height, 0xFF07070C, 0xFF140C26);
        blob(p, width / 5, height / 3, 110, theme[0] & 0xFFFFFF);
        blob(p, width * 4 / 5, height / 4, 100, theme[0] & 0xFFFFFF);
        blob(p, width / 3, height * 4 / 5, 90, theme[0] & 0xFFFFFF);
        for (int i = 0; i < sx.length; i++) {
            float a = 0.3f + 0.7f * Math.abs((float) Math.sin(t * sp[i] + i));
            int x = (int) sx[i], y = (int) sy[i], sz = i % 9 == 0 ? 2 : 1;
            GuiComponent.fill(p, x, y, x + sz, y + sz, ((int) (a * 255) << 24) | 0xE0A3FF);
        }

        // назва
        p.pushPose();
        p.translate(width / 2f, top - 56, 0);
        p.scale(3f, 3f, 1f);
        String logo = "NOVAVISUALS";
        font.drawShadow(p, logo, -font.width(logo) / 2f, 0, 0xFFFFFFFF);
        p.popPose();
        GuiComponent.fill(p, width / 2 - 40, top - 22, width / 2 + 40, top - 21, theme[1]);

        // пункти
        String[] labels = TXT[lang()];
        for (int i = 0; i < labels.length; i++) {
            int y = top + i * (BH + GAP);
            boolean over = mx >= left && mx <= left + BW && my >= y && my <= y + BH;
            hov[i] += ((over ? 1f : 0f) - hov[i]) * 0.25f;
            GuiComponent.fill(p, left, y, left + BW, y + BH, ((int) (0x55 + hov[i] * 0x30) << 24) | 0x14141A);
            int bar = 3 + (int) (hov[i] * 4);
            GuiComponent.fill(p, left, y, left + bar, y + BH, theme[1]);
            font.drawShadow(p, labels[i], left + 14 + hov[i] * 4, y + 8, over ? 0xFFFFFFFF : 0xFFB9B2D0);
        }

        // мова й версія
        String l = (lang() == 0 ? "Мова: Українська" : "Language: English");
        font.drawShadow(p, l, 8, height - 16, theme[2]);
        font.drawShadow(p, "NOVAVISUALS", width - font.width("NOVAVISUALS") - 8, height - 16, 0xFF6F6890);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mx >= 8 && mx <= 150 && my >= height - 18 && my <= height - 6) {
            Settings.d.lang = lang() == 0 ? "en" : "uk";
            Settings.save();
            return true;
        }
        for (int i = 0; i < 5; i++) {
            int y = top + i * (BH + GAP);
            if (mx >= left && mx <= left + BW && my >= y && my <= y + BH) {
                switch (i) {
                    case 0 -> mc.setScreen(new SelectWorldScreen(this));
                    case 1 -> mc.setScreen(new JoinMultiplayerScreen(this));
                    case 2 -> mc.setScreen(new MenuScreen());
                    case 3 -> mc.setScreen(new OptionsScreen(this, mc.options));
                    default -> mc.stop();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean isPauseScreen() { return false; }
}
