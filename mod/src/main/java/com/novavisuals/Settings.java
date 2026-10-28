package com.novavisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Налаштування мода. Зберігаються у config/novavisuals.json */
public final class Settings {
    public static final String[] STYLE_NAMES = {"Хрест", "Точка", "Квадрат", "Зірка"};
    public static final String[][] STYLES = {
        {"...X...", "...X...", "...X...", "XXX.XXX", "...X...", "...X...", "...X..."},
        {".......", ".......", "..XXX..", "..XXX..", "..XXX..", ".......", "......."},
        {"XXX.XXX", "X.....X", "X.....X", ".......", "X.....X", "X.....X", "XXX.XXX"},
        {"X..X..X", ".X.X.X.", "..XXX..", "XXX.XXX", "..XXX..", ".X.X.X.", "X..X..X"}};
    public static final String[] COLOR_NAMES = {"Неон", "Світлий", "Білий", "Фіолет", "М'ята"};
    public static final int[] COLORS = {0xFFB855FF, 0xFFE0A3FF, 0xFFFFFFFF, 0xFF7A2CF7, 0xFF55FFAA};
    public static final String[] ZOOM_NAMES = {"2x", "3x", "4x", "6x"};
    public static final double[] ZOOMS = {2, 3, 4, 6};
    public static final String[] TIME_NAMES = {"День", "Полудень", "Захід", "Ніч"};
    public static final long[] TIMES = {1000, 6000, 12500, 18000};
    public static final String[] FOG_NAMES = {"Фіолет", "Рожевий", "Синій", "Зелений", "Чорний"};
    public static final float[][] FOG = {{0.48f, 0.17f, 0.97f}, {0.95f, 0.35f, 0.75f}, {0.2f, 0.4f, 0.95f}, {0.2f, 0.8f, 0.4f}, {0.02f, 0.02f, 0.04f}};
    public static final String[] HAT_NAMES = {"Вимк.", "Китайський", "Корона"};
    public static final String[] SCALE_NAMES = {"75%", "100%", "125%", "150%"};
    public static final double[] SCALES = {0.75, 1.0, 1.25, 1.5};
    public static final String[] THEME_NAMES = {"Фіолет", "Синій", "Зелений", "Червоний", "Оранж", "Рожевий"};
    public static final int[][] THEMES = {
        {0xFF7A2CF7, 0xFFB855FF, 0xFFE0A3FF}, {0xFF2C6BF7, 0xFF55A0FF, 0xFFA3CCFF}, {0xFF1F9E5B, 0xFF3DDC84, 0xFFA6F0C6},
        {0xFFC4224B, 0xFFFF4D6D, 0xFFFFA3B5}, {0xFFE8650A, 0xFFFF9340, 0xFFFFC999}, {0xFFD0399B, 0xFFFF6FD0, 0xFFFFB3EA}};
    public static final double[] HAND_POS = {-0.5, -0.3, -0.15, 0, 0.15, 0.3, 0.5};
    public static final String[] HAND_POS_NAMES = {"-0.5", "-0.3", "-0.15", "0", "+0.15", "+0.3", "+0.5"};
    public static final double[] HAND_SIZE = {0.6, 0.8, 1.0, 1.2};
    public static final String[] HAND_SIZE_NAMES = {"60%", "80%", "100%", "120%"};
    // {to} - нік гравця, {me} - твій нік. Перший варіант - запит між гравцями з модом Nova Visuals (приймається в меню).
    public static final String[] FRIEND_CMDS = {"/msg {to} [Nova Visuals] NV-FRIEND-REQ {me}", "/friend add {to}", "/f add {to}", "/friends add {to}"};
    public static final String[] FRIEND_CMD_NAMES = {"Nova", "/friend add", "/f add", "/friends add"};

    public static class Marker {
        public String name; public String dim; public double x, y, z;
    }

    public static class Bind {
        public int key; public String cmd;
    }

    public static class Data {
        public boolean crosshair = true; public int crosshairStyle = 0, crosshairColor = 0;
        public boolean zoom = true; public int zoomLevel = 1;
        public boolean fullbright = false;
        public boolean timeOn = false; public int timeIdx = 0;
        public boolean fogOn = false; public int fogIdx = 0;
        public int hat = 0;
        public boolean showFps = true, showCoords = true, showMarkers = true, showClock = false;
        public boolean chatTime = false, noFire = false, cooldowns = true;
        public List<Bind> binds = new ArrayList<>();
        public List<String> pending = new ArrayList<>();   // вхідні запити в друзі
        // режим оптимізації й збережені твої налаштування
        public boolean optimized = false, sVsync = true, sShadows = true;
        public int crosshairScale = 2, menuScale = 1, theme = 0;
        public boolean handOn = false;
        public int handX = 3, handY = 3, handZ = 3, handSize = 2;
        public boolean showFriends = true, friendReq = true, customTitle = true;
        public String lang = "uk";
        public int friendCmd = 0;
        public int sRender = 12, sGraphics = 1, sClouds = 2, sParticles = 0, sAo = 2, sFps = 120, sBlend = 2;
        public boolean autoTpa = false;
        public List<String> friends = new ArrayList<>();
        public List<Marker> markers = new ArrayList<>();
        public Map<String, String> configs = new LinkedHashMap<>();
    }

    public static Data d = new Data();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("novavisuals.json"); }

    public static void load() {
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            Data x = GSON.fromJson(r, Data.class);
            if (x != null) d = x;
        } catch (Exception ignored) { }
        if (d.friends == null) d.friends = new ArrayList<>();
        if (d.markers == null) d.markers = new ArrayList<>();
        if (d.configs == null) d.configs = new LinkedHashMap<>();
        if (d.binds == null) d.binds = new ArrayList<>();
        if (d.pending == null) d.pending = new ArrayList<>();
    }

    /** Зберегти поточні налаштування як пресет (друзі й маркери в пресет не входять). */
    public static void saveConfig(String name) {
        Data copy = GSON.fromJson(GSON.toJson(d), Data.class);
        copy.configs = new LinkedHashMap<>();
        copy.friends = new ArrayList<>();
        copy.markers = new ArrayList<>();
        copy.binds = new ArrayList<>();
        copy.optimized = false;
        copy.pending = new ArrayList<>();
        d.configs.put(name, GSON.toJson(copy));
        save();
    }

    public static void loadConfig(String name) {
        String json = d.configs.get(name);
        if (json == null) return;
        Data x = GSON.fromJson(json, Data.class);
        if (x == null) return;
        x.configs = d.configs;
        x.friends = d.friends;
        x.markers = d.markers;
        x.binds = d.binds;
        x.pending = d.pending;
        x.optimized = d.optimized; x.sRender = d.sRender; x.sGraphics = d.sGraphics; x.sClouds = d.sClouds;
        x.sParticles = d.sParticles; x.sAo = d.sAo; x.sFps = d.sFps; x.sVsync = d.sVsync; x.sBlend = d.sBlend; x.sShadows = d.sShadows;
        d = x;
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(d, w);
        } catch (Exception ignored) { }
    }

    private Settings() { }
}
