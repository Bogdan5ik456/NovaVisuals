package com.novavisuals.fabric121;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * Меню Nova Visuals для 1.21.11 (правий Shift): два стовпчики, плавне листання, плавна поява сторінок.
 * Зібране зі звичайних кнопок Minecraft; прокрутка й клавіша закриття читаються через GLFW.
 */
public class MenuScreen extends Screen {
    private static final String[] TABS = {"HUD", "Гравці", "Ефекти", "Друзі", "Маркери", "Бінди", "Пресети", "Вигляд"};
    private static final int MUTED = 0xFF9A93B3, GREEN = 0xFF55FF99, GRAY = 0xFF8A84A3, WHITE = 0xFFFFFFFF;

    private static final class Row {
        int span = 1, col, h = 24;
        float y;
        AbstractWidget w, e1, e2;
        String label;
        int color = WHITE;
    }

    private int tab, W, H, x0, y0, vx, vy, vw, vh, netVer = -1;
    private float scrollCur, scrollTarget, contentH;
    private long animStart = System.nanoTime(), lastFrame = System.nanoTime();
    private boolean shiftWas = true, leftPrev = true, dragSb;
    private float sbGrab;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> tabBtns = new ArrayList<>();
    private EditBox input;
    private String hint = "";
    private boolean capturing;
    private long capStart;

    public MenuScreen() { this(0); }

    public MenuScreen(int tab) {
        super(Component.literal("Nova Visuals"));
        this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
    }

    /** Відкриває меню. */
    public static void open() { Minecraft.getInstance().setScreen(new MenuScreen(0)); }

    @Override
    protected void init() {
        W = Math.min(440, width - 16);
        H = Math.min(262, height - 16);
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        vx = x0 + 100;
        vy = y0 + 34;
        vw = W - 108;
        vh = H - 34 - 24;
        build(true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Свій фон: без розмиття, світ позаду лишається видно. */
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    // ---------- допоміжні конструктори елементів ----------
    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": ").append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private int colW() { return (vw - 14) / 2; }

    private Button btn(String label, Button.OnPress p, int w) {
        return Button.builder(Component.literal(label), p).bounds(0, 0, w, 20).build();
    }

    private Row add(Row r) {
        rows.add(r);
        if (r.w != null) addRenderableWidget(r.w);
        if (r.e1 != null) addRenderableWidget(r.e1);
        if (r.e2 != null) addRenderableWidget(r.e2);
        return r;
    }

    private void toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
        Row r = new Row();
        r.w = Button.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            Settings.save();
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).bounds(0, 0, colW(), 20).build();
        add(r);
    }

    private void cycle(String name, Supplier<String> val, Runnable next) {
        Row r = new Row();
        r.w = Button.builder(Component.literal(name + ": " + val.get()), b -> {
            next.run();
            Settings.save();
            b.setMessage(Component.literal(name + ": " + val.get()));
        }).bounds(0, 0, colW(), 20).build();
        add(r);
    }

    private void action(String label, int span, Runnable run) {
        Row r = new Row();
        r.span = span;
        r.w = Button.builder(Component.literal(label), b -> run.run()).bounds(0, 0, span == 2 ? vw - 14 : colW(), 20).build();
        add(r);
    }

    private void header(String text) {
        Row r = new Row();
        r.span = 2; r.h = 18; r.label = text; r.color = Widgets.accent();
        add(r);
    }

    private void text(String text, int color) {
        Row r = new Row();
        r.span = 2; r.h = 13; r.label = text; r.color = color;
        add(r);
    }

    /** Рядок: напис ліворуч, до двох кнопок праворуч. */
    private void item(String label, int color, AbstractWidget e1, AbstractWidget e2) {
        Row r = new Row();
        r.span = 2; r.label = label; r.color = color; r.e1 = e1; r.e2 = e2;
        add(r);
    }

    private void inputRow(String placeholder, int max, String btnLabel, Runnable onAdd) {
        Row r = new Row();
        r.span = 2;
        input = new EditBox(font, 0, 0, vw - 14 - 84, 20, Component.literal(placeholder));
        input.setMaxLength(max);
        input.setHint(Component.literal(placeholder));
        r.w = input;
        r.e1 = btn(btnLabel, b -> onAdd.run(), 80);
        add(r);
    }

    private static int idx(float[] arr, float v) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) if (Math.abs(arr[i] - v) < Math.abs(arr[best] - v)) best = i;
        return best;
    }

    // ---------- побудова сторінок ----------
    private void build(boolean resetScroll) {
        clearWidgets();
        rows.clear();
        tabBtns.clear();
        input = null;
        capturing = false;
        if (resetScroll) { scrollCur = 0; scrollTarget = 0; animStart = System.nanoTime(); }
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal((i == tab ? "> " : "") + TABS[i]), x -> {
                tab = idx; hint = "";
                if (idx == 3) NovaNet.refresh();
                build(true);
            }).bounds(x0 + 8, y0 + 34 + i * 23, 84, 20).build();
            tabBtns.add(b);
            addRenderableWidget(b);
        }
        Settings.Data d = Settings.d;
        Minecraft mc = Minecraft.getInstance();
        switch (tab) {
            case 0 -> {
                header("Віджети HUD");
                toggle("Info", () -> d.infoOn, v -> d.infoOn = v);
                toggle("FPS", () -> d.showFps, v -> d.showFps = v);
                toggle("Годинник", () -> d.showClock, v -> d.showClock = v);
                toggle("Координати", () -> d.showCoords, v -> d.showCoords = v);
                toggle("День світу", () -> d.showWorldTime, v -> d.showWorldTime = v);
                toggle("Potions", () -> d.potionsOn, v -> d.potionsOn = v);
                toggle("Keybinds", () -> d.keybindsOn, v -> d.keybindsOn = v);
                toggle("Target HUD", () -> d.showHealth, v -> d.showHealth = v);
                toggle("Броня", () -> d.showArmor, v -> d.showArmor = v);
                toggle("Маркери", () -> d.showMarkers, v -> d.showMarkers = v);
                toggle("Друзі онлайн", () -> d.showFriends, v -> d.showFriends = v);
                header("Розташування");
                action("Редагувати HUD (перетягнути)", 2, () -> Minecraft.getInstance().setScreen(new HudEditScreen(0)));
                action("Скинути позиції", 1, () -> { Widgets.resetPositions(); hint = "Позиції скинуто"; });
            }
            case 1 -> {
                header("Гравці поруч");
                toggle("Увімкнено", () -> d.radarOn, v -> d.radarOn = v);
                cycle("Режим", () -> Settings.RADAR_MODES[d.radarMode], () -> d.radarMode = (d.radarMode + 1) % Settings.RADAR_MODES.length);
                cycle("Дальність", () -> Settings.RADAR_RANGES[d.radarRange] + " м", () -> d.radarRange = (d.radarRange + 1) % Settings.RADAR_RANGES.length);
                cycle("Скільки гравців", () -> String.valueOf(Settings.RADAR_MAX[d.radarMax]), () -> d.radarMax = (d.radarMax + 1) % Settings.RADAR_MAX.length);
                toggle("Лише друзі", () -> d.radarFriendsOnly, v -> d.radarFriendsOnly = v);
                toggle("Здоров'я", () -> d.radarHealth, v -> d.radarHealth = v);
                toggle("Сторона світу", () -> d.radarSide, v -> d.radarSide = v);
                toggle("Координати (блоки)", () -> d.radarCoords, v -> d.radarCoords = v);
                header("Пам'ять позицій");
                text("Гравець, що відійшов далеко, лишається на радарі (тьмяно, з часом).", MUTED);
                cycle("Пам'ятати", () -> Settings.MEMORY_NAMES[d.radarMemory], () -> d.radarMemory = (d.radarMemory + 1) % Settings.MEMORY_NAMES.length);
                action("Очистити пам'ять", 1, () -> { Radar.clear(); hint = "Пам'ять радара очищено"; });
            }
            case 2 -> {
                header("Удар і вбивство");
                toggle("Hit FX", () -> d.hitFx, v -> d.hitFx = v);
                toggle("Підсвітка цілі", () -> d.hitHighlight, v -> d.hitHighlight = v);
                toggle("Kill FX", () -> d.killFx, v -> d.killFx = v);
                toggle("Звук вбивства", () -> d.killSound, v -> d.killSound = v);
                toggle("Kill для всіх", () -> d.killAny, v -> d.killAny = v);
                toggle("Бризки у воді", () -> d.splashFx, v -> d.splashFx = v);
                toggle("Іскри над предметами", () -> d.itemGlow, v -> d.itemGlow = v);
                header("Вигляд ефектів");
                cycle("Hit частинки", () -> Settings.PART_NAMES[d.hitStyle % 10], () -> d.hitStyle = (d.hitStyle + 1) % 10);
                cycle("Hit форма", () -> Settings.HIT_SHAPES[d.hitShape % Settings.HIT_SHAPES.length], () -> d.hitShape = (d.hitShape + 1) % Settings.HIT_SHAPES.length);
                cycle("Kill частинки", () -> Settings.PART_NAMES[d.killStyle % 10], () -> d.killStyle = (d.killStyle + 1) % 10);
                cycle("Kill форма", () -> Settings.KILL_SHAPES[d.killShape % Settings.KILL_SHAPES.length], () -> d.killShape = (d.killShape + 1) % Settings.KILL_SHAPES.length);
                cycle("Кількість", () -> Settings.AMOUNT_NAMES[idx(Settings.AMOUNTS, d.fxAmount)], () -> d.fxAmount = Settings.AMOUNTS[(idx(Settings.AMOUNTS, d.fxAmount) + 1) % Settings.AMOUNTS.length]);
                cycle("Швидкість", () -> Settings.AMOUNT_NAMES[idx(Settings.AMOUNTS, d.fxSpeed)], () -> d.fxSpeed = Settings.AMOUNTS[(idx(Settings.AMOUNTS, d.fxSpeed) + 1) % Settings.AMOUNTS.length]);
                action("Test Effects (закрити меню й показати)", 2, () -> { onClose(); Fx.test(Minecraft.getInstance()); });
            }
            case 3 -> friendsTab(d);
            case 4 -> {
                header("Маркери (відстань у блоках на HUD)");
                inputRow("Назва маркера", 24, "Додати тут", () -> {
                    if (mc.player == null || mc.level == null) return;
                    Settings.Marker m = new Settings.Marker();
                    String n = input.getValue().trim();
                    m.name = n.isEmpty() ? "M" + (d.markers.size() + 1) : n;
                    m.dim = mc.level.dimension().toString();
                    m.x = mc.player.getX(); m.y = mc.player.getY(); m.z = mc.player.getZ();
                    d.markers.add(m);
                    Settings.save();
                    hint = "Маркер додано";
                    rebuild();
                });
                if (d.markers.isEmpty()) text("Маркерів поки немає", MUTED);
                for (int i = 0; i < d.markers.size(); i++) {
                    final int k = i;
                    Settings.Marker m = d.markers.get(i);
                    item(m.name + "  " + Math.round(m.x) + " " + Math.round(m.y) + " " + Math.round(m.z), WHITE,
                            btn("X", b -> { d.markers.remove(k); Settings.save(); rebuild(); }, 22), null);
                }
            }
            case 5 -> {
                header("Бінди: клавіша → команда або повідомлення");
                inputRow("Команда, напр. /home", 60, capturing ? "Тисни клавішу…" : "Клавіша", () -> {
                    if (input.getValue().trim().isEmpty()) { hint = "Спершу впиши команду"; return; }
                    capturing = true; capStart = System.nanoTime(); hint = "Натисни клавішу (Esc - скасувати)";
                });
                if (d.binds.isEmpty()) text("Біндів поки немає", MUTED);
                for (int i = 0; i < d.binds.size(); i++) {
                    final int k = i;
                    Settings.Bind b = d.binds.get(i);
                    item("[" + Keys.name(b.key) + "]  " + b.cmd, WHITE, btn("X", x -> { d.binds.remove(k); Settings.save(); rebuild(); }, 22), null);
                }
            }
            case 6 -> {
                header("Пресети налаштувань");
                inputRow("Назва пресета", 20, "Зберегти", () -> {
                    String n = input.getValue().trim();
                    if (n.isEmpty()) { hint = "Впиши назву"; return; }
                    Settings.saveConfig(n);
                    hint = "Пресет збережено";
                    rebuild();
                });
                if (d.configs.isEmpty()) text("Пресетів поки немає", MUTED);
                for (String name : new ArrayList<>(d.configs.keySet())) {
                    item(name, WHITE,
                            btn("X", b -> { d.configs.remove(name); Settings.save(); rebuild(); }, 22),
                            btn("Завантажити", b -> { Settings.loadConfig(name); hint = "Пресет завантажено"; rebuild(); }, 84));
                }
            }
            default -> {
                header("Вигляд");
                cycle("Тема", () -> Settings.THEME_NAMES[d.theme % Settings.THEME_NAMES.length], () -> d.theme = (d.theme + 1) % Settings.THEME_NAMES.length);
                toggle("Мітка NV для сервера", () -> d.announce, v -> d.announce = v);
                toggle("Авто /tpaccept від друзів", () -> d.autoTpa, v -> d.autoTpa = v);
                header("Поки лише в мода для 1.18.2");
                text("Зум, колір неба й туману, час доби, fullbright, свій приціл,", MUTED);
                text("анімація руки, блиск чарів, капелюхи, режим оптимізації, чат з часом.", MUTED);
                text("Вони потребують міксинів; їх додаємо наступним кроком.", MUTED);
                text("Версія мода: " + Settings.VERSION, MUTED);
            }
        }
        layoutRows();
        netVer = NovaNet.version;
    }

    private void friendsTab(Settings.Data d) {
        header("Друзі через сайт Nova Visuals");
        text(NovaNet.linked() ? "Підключено: " + NovaNet.user : "Не підключено: лаунчер → Профіль → Увійти через сайт", NovaNet.linked() ? GREEN : GRAY);
        action("Оновити список", 1, NovaNet::refresh);
        for (String who : NovaNet.incoming) {
            item("Запит: " + who, 0xFFE0A3FF, btn("Прийняти", b -> NovaNet.action("accept", who), 66), btn("X", b -> NovaNet.action("decline", who), 22));
        }
        for (NovaNet.Friend f : NovaNet.friends) {
            String st = f.online ? (f.inGame ? "у грі" + (f.server.isEmpty() ? "" : " · " + f.server) : "в лаунчері") : "офлайн";
            item((f.online ? "● " : "○ ") + f.name + "  " + st, f.online ? GREEN : GRAY, btn("X", b -> NovaNet.action("remove", f.name), 22), null);
        }
        if (NovaNet.linked() && NovaNet.friends.isEmpty() && NovaNet.incoming.isEmpty()) text("Друзів поки немає", MUTED);
        inputRow("Нік друга на сайті", 16, "Додати", () -> {
            String v = input.getValue().trim();
            if (!v.matches("[A-Za-z0-9_]{3,16}")) { hint = "Нік: 3-16 символів, латиниця, цифри, _"; return; }
            if (NovaNet.linked()) { NovaNet.action("add", v, msg -> hint = msg); input.setValue(""); }
            else { if (!d.friends.contains(v)) d.friends.add(v); Settings.save(); hint = "Додано до ручного списку"; input.setValue(""); rebuild(); }
        });
        if (!d.friends.isEmpty()) {
            header("Ручний список (без сайту)");
            for (String n : new ArrayList<>(d.friends)) item(n, WHITE, btn("X", b -> { d.friends.remove(n); Settings.save(); rebuild(); }, 22), null);
        }
    }

    /** Перебудова без стрибка прокрутки й без втрати введеного тексту. */
    private void rebuild() {
        float sc = scrollCur, st = scrollTarget;
        String iv = input != null ? input.getValue() : "";
        build(false);
        if (input != null) input.setValue(iv);
        scrollCur = sc; scrollTarget = st;
    }

    private void layoutRows() {
        float y = 0;
        int col = 0;
        for (Row r : rows) {
            boolean full = r.span == 2 || (r.w == null);
            if (full) {
                if (col == 1) { y += 24; col = 0; }
                r.col = 0; r.y = y; y += r.h;
            } else {
                r.col = col; r.y = y;
                if (col == 0) col = 1; else { col = 0; y += 24; }
            }
        }
        if (col == 1) y += 24;
        contentH = y + 4;
    }

    // ---------- малювання ----------
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        int acc = Widgets.accent();
        int violet = Settings.THEMES[Math.max(0, Math.min(Settings.THEMES.length - 1, Settings.d.theme))][0];
        int soft = Settings.THEMES[Math.max(0, Math.min(Settings.THEMES.length - 1, Settings.d.theme))][2];

        if (tab == 3 && netVer != NovaNet.version) rebuild();   // дані з сайту оновилися

        // анімація: плавне листання й плавна поява сторінки
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float maxScroll = Math.max(0f, contentH - vh);
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-dt * 14f));
        if (Math.abs(scrollTarget - scrollCur) < 0.15f) scrollCur = scrollTarget;
        float t = Math.min(1f, (now - animStart) / 220_000_000f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        int slide = Math.round((1f - ease) * 28f);

        // панель
        g.fill(0, 0, width, height, 0x55000000);
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, violet);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF00B0B0F);
        g.fill(x0, y0, x0 + 98, y0 + H, 0xFF14141A);
        g.drawString(font, "NOVA", x0 + 10, y0 + 8, acc);
        g.drawString(font, "VISUALS", x0 + 10, y0 + 18, soft);
        g.drawString(font, TABS[tab], vx, y0 + 12, soft);
        g.fill(vx, y0 + 26, vx + vw - 8, y0 + 27, 0x44000000 | (acc & 0xFFFFFF));
        for (Button b : tabBtns) b.render(g, mx, my, pt);

        // вміст: у вікні прокрутки
        int cw = colW();
        g.enableScissor(vx, vy, vx + vw - 8, vy + vh);
        for (Row r : rows) {
            float ry = vy + r.y - scrollCur;
            boolean vis = ry >= vy - 12 && ry + r.h <= vy + vh + 12;
            int rx = vx + slide;
            if (r.w != null) {
                if (r.span == 1) { r.w.setX(rx + r.col * (cw + 6)); r.w.setWidth(cw); }
                else {
                    int right = vx + vw - 8 + slide, w1 = r.e1 != null ? r.e1.getWidth() : 0, w2 = r.e2 != null ? r.e2.getWidth() : 0;
                    int used = (w1 > 0 ? w1 + 4 : 0) + (w2 > 0 ? w2 + 4 : 0);
                    if (r.w instanceof EditBox) { r.w.setX(rx); r.w.setWidth(vw - 8 - used); }
                    else { r.w.setX(rx); r.w.setWidth(vw - 8); }
                }
                r.w.setY(Math.round(ry));
                r.w.visible = vis;
            }
            if (r.e1 != null) {
                int right = vx + vw - 8 + slide;
                r.e1.setX(right - r.e1.getWidth());
                r.e1.setY(Math.round(ry) + (r.w == null ? 2 : 0));
                r.e1.visible = vis;
                if (r.e2 != null) {
                    r.e2.setX(right - r.e1.getWidth() - 4 - r.e2.getWidth());
                    r.e2.setY(Math.round(ry) + (r.w == null ? 2 : 0));
                    r.e2.visible = vis;
                }
            }
            if (!vis) continue;
            if (r.label != null) {
                int maxW = vw - 16 - (r.e1 != null ? r.e1.getWidth() + 6 : 0) - (r.e2 != null ? r.e2.getWidth() + 6 : 0);
                int ty = Math.round(ry) + (r.h >= 24 ? 8 : r.h >= 18 ? 6 : 2);
                g.drawString(font, font.plainSubstrByWidth(r.label, Math.max(40, maxW)), rx + 2, ty, r.color);
            }
            if (r.w != null) r.w.render(g, mx, my, pt);
            if (r.e1 != null) r.e1.render(g, mx, my, pt);
            if (r.e2 != null) r.e2.render(g, mx, my, pt);
        }
        g.disableScissor();

        // смуга прокрутки (її можна тягнути мишею)
        boolean left = Keys.mouse(0);
        if (contentH > vh + 1) {
            int sx = vx + vw - 5, th = Math.max(16, Math.round(vh * (vh / contentH)));
            int ty = vy + Math.round((vh - th) * (maxScroll <= 0 ? 0 : scrollCur / maxScroll));
            boolean over = mx >= sx - 3 && mx <= sx + 6 && my >= vy && my <= vy + vh;
            if (left && !leftPrev && over) { dragSb = true; sbGrab = (my >= ty && my <= ty + th) ? my - ty : th / 2f; }
            if (!left) dragSb = false;
            if (dragSb && vh - th > 0) {
                float p = (my - sbGrab - vy) / (float) (vh - th);
                scrollTarget = Math.max(0f, Math.min(maxScroll, p * maxScroll));
                scrollCur = scrollTarget;
            }
            g.fill(sx, vy, sx + 3, vy + vh, 0x33FFFFFF);
            g.fill(sx, ty, sx + 3, ty + th, dragSb || over ? soft : acc);
        }
        leftPrev = left;

        // підвал
        if (!hint.isEmpty()) g.drawString(font, font.plainSubstrByWidth(hint, vw - 10), vx, y0 + H - 14, 0xFFFFD166);
        g.drawString(font, "Right Shift - закрити", x0 + 8, y0 + H - 14, MUTED);

        // призначення клавіші для біндів
        if (capturing && now - capStart > 200_000_000L) {
            int k = Keys.anyPressed();
            if (k == -2) { capturing = false; hint = "Скасовано"; rebuild(); }
            else if (k > 0 && input != null) {
                Settings.Bind b = new Settings.Bind();
                b.key = k; b.cmd = input.getValue().trim();
                Settings.d.binds.add(b);
                Settings.save();
                capturing = false; hint = "Бінд додано: " + Keys.name(k);
                rebuild();
            }
        }

        // закриття правим Shift (не закриваємо, поки пишеш у полі)
        boolean down = Keys.rightShift();
        if (down && !shiftWas && !(input != null && input.isFocused()) && !capturing) { onClose(); return; }
        shiftWas = down;
    }

    /** Колесо миші: плавне листання. (Без @Override, щоб не залежати від точного підпису в цій версії.) */
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollTarget -= (float) sy * 36f;
        return true;
    }
}
