package com.novavisuals;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Меню Nova Visuals (відкривається правим Shift). */
public class MenuScreen extends Screen {
    private static final int W = 380, H = 250, ROWH = 34;
    private static final int BG = 0xF00B0B0F, SIDE = 0xFF14141A, CARD = 0xFF1E1B2E, VIOLET = 0xFF7A2CF7, NEON = 0xFFB855FF,
            SOFT = 0xFFE0A3FF, MUTED = 0xFF9A93B3, OFF = 0xFF2C2745;
    private static final String[] TABS = {"Visuals", "HUD", "Utilities", "Markers", "Friends", "Configs"};

    private record Row(String title, String desc, BooleanSupplier on, Runnable toggle, Supplier<String> val, Runnable cycle) { }

    private int x0, y0, tab = 0, scroll = 0;
    private EditBox input;
    private final List<Row> rows = new ArrayList<>();
    private String hint = "";
    private boolean listening = false;
    private String pendingCmd = "";

    private static String keyName(int key) {
        try { return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString(); }
        catch (Exception e) { return "KEY " + key; }
    }

    public MenuScreen() { super(new TextComponent("Nova Visuals")); }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        input = new EditBox(this.font, x0 + 110, y0 + H - 30, 150, 16, new TextComponent(""));
        input.setMaxLength(24);
        addRenderableWidget(input);
        build();
    }

    private int viewBottom() { return y0 + H - (tab >= 2 ? 40 : 10); }
    private int viewH() { return viewBottom() - (y0 + 10); }

    private void build() {
        rows.clear();
        Settings.Data d = Settings.d;
        input.visible = tab >= 2;
        listening = false;
        input.setValue("");
        switch (tab) {
            case 0 -> {
                rows.add(new Row("Optimize FPS", "Швидка графіка, менша дальність, без V-Sync", null, null,
                        () -> d.optimized ? "Restore" : "Optimize", Optimizer::toggle));
                rows.add(new Row("Crosshair", "Власний приціл", () -> d.crosshair, () -> d.crosshair = !d.crosshair,
                        () -> Settings.STYLE_NAMES[d.crosshairStyle], () -> d.crosshairStyle = (d.crosshairStyle + 1) % 4));
                rows.add(new Row("Crosshair Color", "Колір прицілу", () -> true, null,
                        () -> Settings.COLOR_NAMES[d.crosshairColor], () -> d.crosshairColor = (d.crosshairColor + 1) % 5));
                rows.add(new Row("Zoom", "Утримуй C для наближення", () -> d.zoom, () -> d.zoom = !d.zoom,
                        () -> Settings.ZOOM_NAMES[d.zoomLevel], () -> d.zoomLevel = (d.zoomLevel + 1) % 4));
                rows.add(new Row("Fullbright", "Максимальна яскравість", () -> d.fullbright, () -> d.fullbright = !d.fullbright, null, null));
                rows.add(new Row("Custom World", "Час доби (тільки для тебе)", () -> d.timeOn, () -> d.timeOn = !d.timeOn,
                        () -> Settings.TIME_NAMES[d.timeIdx], () -> d.timeIdx = (d.timeIdx + 1) % 4));
                rows.add(new Row("Custom Sky", "Колір туману й неба", () -> d.fogOn, () -> d.fogOn = !d.fogOn,
                        () -> Settings.FOG_NAMES[d.fogIdx], () -> d.fogIdx = (d.fogIdx + 1) % 5));
                rows.add(new Row("Cosmetics", "Капелюх на голову (бачиш тільки ти)", () -> d.hat != 0, () -> d.hat = d.hat == 0 ? 1 : 0,
                        () -> Settings.HAT_NAMES[d.hat], () -> d.hat = (d.hat % 2) + 1));
            }
            case 1 -> {
                rows.add(new Row("FPS", "Показувати кадри на секунду", () -> d.showFps, () -> d.showFps = !d.showFps, null, null));
                rows.add(new Row("Coordinates", "Координати й напрям", () -> d.showCoords, () -> d.showCoords = !d.showCoords, null, null));
                rows.add(new Row("Markers", "Список маркерів із відстанню", () -> d.showMarkers, () -> d.showMarkers = !d.showMarkers, null, null));
                rows.add(new Row("Clock", "Реальний час", () -> d.showClock, () -> d.showClock = !d.showClock, null, null));
                rows.add(new Row("Cooldowns", "Смужки кулдаунів над хотбаром", () -> d.cooldowns, () -> d.cooldowns = !d.cooldowns, null, null));
            }
            case 2 -> {
                rows.add(new Row("Chat Time", "Час перед кожним повідомленням", () -> d.chatTime, () -> d.chatTime = !d.chatTime, null, null));
                rows.add(new Row("No Fire Overlay", "Прибрати вогонь на екрані", () -> d.noFire, () -> d.noFire = !d.noFire, null, null));
                for (Settings.Bind b : new ArrayList<>(d.binds))
                    rows.add(new Row("Бінд: " + keyName(b.key), b.cmd, null, null, () -> "Видалити", () -> { d.binds.remove(b); Settings.save(); build(); }));
            }
            case 4 -> {
                rows.add(new Row("Auto /tpa", "Приймати /tpa лише від друзів", () -> d.autoTpa, () -> d.autoTpa = !d.autoTpa, null, null));
                for (String f : new ArrayList<>(d.friends))
                    rows.add(new Row(f, "Друг", null, null, () -> "Видалити", () -> { d.friends.remove(f); Settings.save(); build(); }));
            }
            case 5 -> {
                for (String c : new ArrayList<>(d.configs.keySet()))
                    rows.add(new Row(c, "ЛКМ - завантажити, ПКМ - видалити", null, null, () -> "Завантажити",
                            () -> { Settings.loadConfig(c); build(); }));
            }
            default -> {
                for (Settings.Marker m : new ArrayList<>(d.markers))
                    rows.add(new Row(m.name, String.format("%.0f %.0f %.0f", m.x, m.y, m.z), null, null, () -> "Видалити",
                            () -> { d.markers.remove(m); Settings.save(); build(); }));
            }
        }
        scroll = 0;
    }

    private void fillBox(PoseStack p, int x1, int y1, int x2, int y2, int c) { GuiComponent.fill(p, x1, y1, x2, y2, c); }

    @Override
    public void render(PoseStack p, int mx, int my, float pt) {
        fillBox(p, 0, 0, width, height, 0x66000000);
        fillBox(p, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, VIOLET);
        fillBox(p, x0, y0, x0 + W, y0 + H, BG);
        fillBox(p, x0, y0, x0 + 100, y0 + H, SIDE);
        font.drawShadow(p, "NOVA", x0 + 12, y0 + 12, NEON);
        font.drawShadow(p, "VISUALS", x0 + 12, y0 + 22, SOFT);
        for (int i = 0; i < TABS.length; i++) {
            int ty = y0 + 46 + i * 24;
            if (i == tab) fillBox(p, x0 + 6, ty, x0 + 94, ty + 20, VIOLET);
            font.drawShadow(p, TABS[i], x0 + 14, ty + 6, i == tab ? 0xFFFFFFFF : MUTED);
        }
        font.draw(p, "Right Shift - закрити", x0 + 8, y0 + H - 14, MUTED);

        int cx = x0 + 110, cw = W - 120, top = y0 + 10, bottom = viewBottom();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + i * ROWH - scroll;
            if (ry < top || ry + ROWH - 4 > bottom) continue;
            fillBox(p, cx, ry, cx + cw, ry + ROWH - 4, CARD);
            font.drawShadow(p, r.title(), cx + 8, ry + 5, 0xFFFFFFFF);
            font.draw(p, r.desc(), cx + 8, ry + 17, MUTED);
            if (r.on() != null && r.toggle() != null) {
                boolean on = r.on().getAsBoolean();
                int tx = cx + cw - 34;
                fillBox(p, tx, ry + 8, tx + 26, ry + 20, on ? VIOLET : OFF);
                fillBox(p, on ? tx + 15 : tx + 1, ry + 9, on ? tx + 25 : tx + 11, ry + 19, on ? SOFT : MUTED);
            }
            if (r.val() != null) {
                int bx = cx + cw - (r.toggle() != null ? 120 : 80);
                fillBox(p, bx, ry + 8, bx + 76, ry + 22, OFF);
                font.draw(p, r.val().get(), bx + 6, ry + 11, SOFT);
            }
        }
        if (rows.isEmpty()) font.draw(p, tab == 3 ? "Маркерів поки немає" : tab == 5 ? "Збережених пресетів немає" : "Порожньо", cx + 8, top + 8, MUTED);

        if (tab >= 2) {
            fillBox(p, x0 + 270, y0 + H - 32, x0 + 366, y0 + H - 12, VIOLET);
            font.drawShadow(p, tab == 2 ? "Додати бінд" : tab == 4 ? "Додати друга" : tab == 5 ? "Зберегти пресет" : "Маркер тут", x0 + 278, y0 + H - 26, 0xFFFFFFFF);
            if (!hint.isEmpty()) font.draw(p, hint, cx, y0 + H - 44, 0xFFFF6B8B);
        }
        super.render(p, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < TABS.length; i++) {
            int ty = y0 + 46 + i * 24;
            if (mx >= x0 + 6 && mx <= x0 + 94 && my >= ty && my <= ty + 20) { tab = i; hint = ""; build(); return true; }
        }
        int cx = x0 + 110, cw = W - 120, top = y0 + 10, bottom = viewBottom();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = top + i * ROWH - scroll;
            if (ry < top || ry + ROWH - 4 > bottom) continue;
            if (my < ry || my > ry + ROWH - 4) continue;
            int tx = cx + cw - 34;
            if (r.toggle() != null && mx >= tx && mx <= tx + 26) { r.toggle().run(); Settings.save(); return true; }
            if (r.cycle() != null) {
                int bx = cx + cw - (r.toggle() != null ? 120 : 80);
                if (mx >= bx && mx <= bx + 76) {
                    if (tab == 5 && button == 1) { Settings.d.configs.remove(r.title()); Settings.save(); build(); }
                    else { r.cycle().run(); Settings.save(); }
                    return true;
                }
            }
        }
        if (tab >= 2 && mx >= x0 + 270 && mx <= x0 + 366 && my >= y0 + H - 32 && my <= y0 + H - 12) { addEntry(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    private void addEntry() {
        String v = input.getValue().trim();
        Minecraft mc = Minecraft.getInstance();
        if (tab == 2) {
            if (v.isEmpty()) { hint = "Впиши команду, наприклад /home"; return; }
            pendingCmd = v; listening = true; hint = "Натисни клавішу для бінда (Esc - скасувати)";
            return;
        } else if (tab == 5) {
            if (v.isEmpty()) { hint = "Впиши назву пресета"; return; }
            Settings.saveConfig(v);
        } else if (tab == 4) {
            if (!v.matches("[A-Za-z0-9_]{3,16}")) { hint = "Нік: 3-16 символів, латиниця, цифри, _"; return; }
            if (!Settings.d.friends.contains(v)) Settings.d.friends.add(v);
        } else {
            if (v.isEmpty() || mc.player == null || mc.level == null) { hint = "Впиши назву маркера"; return; }
            Settings.Marker m = new Settings.Marker();
            m.name = v; m.dim = mc.level.dimension().location().toString();
            m.x = mc.player.getX(); m.y = mc.player.getY(); m.z = mc.player.getZ();
            Settings.d.markers.add(m);
        }
        hint = "";
        Settings.save();
        build();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, rows.size() * ROWH - viewH());
        scroll = Math.max(0, Math.min(max, scroll - (int) (delta * 17)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listening) {
            listening = false;
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                Settings.Bind b = new Settings.Bind();
                b.key = key; b.cmd = pendingCmd;
                Settings.d.binds.add(b);
                Settings.save();
                hint = "";
                build();
            } else hint = "";
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && !input.isFocused()) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() { Settings.save(); super.onClose(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
