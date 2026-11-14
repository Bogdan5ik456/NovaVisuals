package com.novavisuals.fabric121;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Меню Nova Visuals для 1.21.11 (відкривається правим Shift).
 * Зібране зі звичайних кнопок Minecraft, тому не залежить від дрібних змін у клавіатурному й мишачому API.
 */
public class MenuScreen extends Screen {
    private static final int W = 330, H = 214;
    private static final int NEON = 0xFFB855FF, VIOLET = 0xFF7A2CF7, SOFT = 0xFFE0A3FF, MUTED = 0xFF9A93B3, GREEN = 0xFF55FF99, GRAY = 0xFF8A84A3;
    private static final String[] TABS = {"HUD", "Гравці", "Друзі"};

    private int tab = 0, x0, y0, netVer = -1;
    private boolean shiftWas = true;   // правий Shift ще затиснутий після відкриття
    private EditBox input;
    private String hint = "";
    private final List<Object[]> textRows = new ArrayList<>();   // {x, y, текст, колір}

    public MenuScreen() { super(Component.literal("Nova Visuals")); }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        build();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- елементи ----------
    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": ").append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            Settings.save();
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).bounds(x, y, w, 20).build());
    }

    private void cycle(int x, int y, int w, String name, Supplier<String> val, Runnable next) {
        addRenderableWidget(Button.builder(Component.literal(name + ": " + val.get()), b -> {
            next.run();
            Settings.save();
            b.setMessage(Component.literal(name + ": " + val.get()));
        }).bounds(x, y, w, 20).build());
    }

    private void build() {
        clearWidgets();
        textRows.clear();
        input = null;
        Settings.Data d = Settings.d;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal((i == tab ? "> " : "") + TABS[i]), b -> {
                tab = idx; hint = "";
                if (idx == 2) NovaNet.refresh();
                build();
            }).bounds(x0 + 8, y0 + 34 + i * 24, 72, 20).build());
        }
        int cx = x0 + 92, cw = W - 102, y = y0 + 34;
        switch (tab) {
            case 0 -> {
                toggle(cx, y, cw, "FPS", () -> d.fps, v -> d.fps = v); y += 24;
                toggle(cx, y, cw, "Координати", () -> d.coords, v -> d.coords = v); y += 24;
                toggle(cx, y, cw, "Здоров'я", () -> d.health, v -> d.health = v); y += 24;
                toggle(cx, y, cw, "Броня", () -> d.armor, v -> d.armor = v); y += 24;
                toggle(cx, y, cw, "Мітка NV для сервера", () -> d.announce, v -> d.announce = v);
            }
            case 1 -> {
                toggle(cx, y, cw, "Гравці поруч", () -> d.radarOn, v -> d.radarOn = v); y += 24;
                cycle(cx, y, cw, "Режим", () -> modeName(d.radarMode), () -> d.radarMode = (d.radarMode + 1) % 3); y += 24;
                cycle(cx, y, cw, "Дальність", () -> Settings.RADAR_RANGES[d.radarRange] + " м", () -> d.radarRange = (d.radarRange + 1) % Settings.RADAR_RANGES.length); y += 24;
                cycle(cx, y, cw, "Скільки гравців", () -> String.valueOf(Settings.RADAR_MAX[d.radarMax]), () -> d.radarMax = (d.radarMax + 1) % Settings.RADAR_MAX.length); y += 24;
                toggle(cx, y, cw, "Лише друзі", () -> d.radarFriendsOnly, v -> d.radarFriendsOnly = v);
            }
            default -> friends(cx, y, cw);
        }
        netVer = NovaNet.version;
    }

    private static String modeName(int m) { return m == 0 ? "Список" : m == 1 ? "Карта" : "Картки"; }

    private void friends(int cx, int y, int cw) {
        textRows.add(new Object[]{cx, y + 5, NovaNet.linked() ? "Сайт: " + NovaNet.user : "Не підключено (лаунчер → Профіль → Увійти через сайт)", NovaNet.linked() ? GREEN : GRAY});
        addRenderableWidget(Button.builder(Component.literal("Оновити"), b -> NovaNet.refresh()).bounds(cx + cw - 64, y, 64, 18).build());
        y += 24;
        int shown = 0;
        for (String who : NovaNet.incoming) {
            if (shown >= 2) break;
            textRows.add(new Object[]{cx + 4, y + 5, "Запит: " + who, SOFT});
            addRenderableWidget(Button.builder(Component.literal("Прийняти"), b -> NovaNet.action("accept", who)).bounds(cx + cw - 70, y, 70, 18).build());
            y += 21; shown++;
        }
        int n = 0;
        for (NovaNet.Friend f : NovaNet.friends) {
            if (n >= (shown > 0 ? 4 : 5) - (shown > 1 ? 1 : 0)) break;
            String st = f.online ? (f.inGame ? "у грі" + (f.server.isEmpty() ? "" : " · " + f.server) : "в лаунчері") : "офлайн";
            textRows.add(new Object[]{cx + 4, y + 5, (f.online ? "● " : "○ ") + f.name + "  " + st, f.online ? GREEN : GRAY});
            final String name = f.name;
            addRenderableWidget(Button.builder(Component.literal("X"), b -> NovaNet.action("remove", name)).bounds(cx + cw - 20, y, 20, 18).build());
            y += 21; n++;
        }
        if (n == 0 && shown == 0 && NovaNet.linked()) textRows.add(new Object[]{cx + 4, y + 5, "Друзів поки немає", MUTED});
        input = new EditBox(font, cx, y0 + H - 30, cw - 72, 18, Component.literal("Нік"));
        input.setMaxLength(16);
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Додати"), b -> {
            String v = input.getValue().trim();
            if (!v.matches("[A-Za-z0-9_]{3,16}")) { hint = "Нік: 3-16 символів, латиниця, цифри, _"; return; }
            if (!NovaNet.linked()) { hint = "Спершу увійди через сайт у лаунчері"; return; }
            NovaNet.action("add", v, msg -> hint = msg);
            input.setValue("");
        }).bounds(cx + cw - 68, y0 + H - 31, 68, 20).build());
    }

    // ---------- малювання ----------
    /** Свій фон замість розмитого: темна панель із фіолетовою рамкою. */
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x66000000);
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, VIOLET);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF00B0B0F);
        g.fill(x0, y0, x0 + 86, y0 + H, 0xFF14141A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (tab == 2 && netVer != NovaNet.version) {   // дані з сайту оновилися
            String iv = input != null ? input.getValue() : "";
            build();
            if (input != null) input.setValue(iv);
        }
        super.render(g, mx, my, pt);
        g.drawString(font, "NOVA", x0 + 10, y0 + 8, NEON);
        g.drawString(font, "VISUALS", x0 + 10, y0 + 18, SOFT);
        String ver = "v" + NovaNet.VERSION;
        g.drawString(font, ver, x0 + W - font.width(ver) - 6, y0 + 6, MUTED);
        g.drawString(font, "Right Shift - закрити", x0 + 8, y0 + H - 12, MUTED);
        for (Object[] r : textRows) g.drawString(font, font.plainSubstrByWidth((String) r[2], W - 130), (int) r[0], (int) r[1], (int) r[3]);
        if (tab == 2 && !hint.isEmpty()) g.drawString(font, font.plainSubstrByWidth(hint, W - 110), x0 + 92, y0 + H - 44, 0xFFFF6B8B);

        boolean down = Keys.rightShift();   // закриття правим Shift (не закриваємо, поки пишеш нік)
        if (down && !shiftWas && !(input != null && input.isFocused())) { onClose(); return; }
        shiftWas = down;
    }

    /** Відкриває меню. */
    public static void open() { Minecraft.getInstance().setScreen(new MenuScreen()); }

    /** Права клавіша Shift, напряму через GLFW (не залежить від клавіш Minecraft). */
    public static final class Keys {
        public static boolean rightShift() {
            long w = GLFW.glfwGetCurrentContext();
            return w != 0L && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        }

        private Keys() { }
    }
}
