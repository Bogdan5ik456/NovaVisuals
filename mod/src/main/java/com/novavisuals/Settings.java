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

    public static class Marker {
        public String name; public String dim; public double x, y, z;
    }

    public static class Data {
        public boolean crosshair = true; public int crosshairStyle = 0, crosshairColor = 0;
        public boolean zoom = true; public int zoomLevel = 1;
        public boolean fullbright = false;
        public boolean timeOn = false; public int timeIdx = 0;
        public boolean fogOn = false; public int fogIdx = 0;
        public int hat = 0;
        public boolean showFps = true, showCoords = true, showMarkers = true, showClock = false;
        public boolean chatTime = false, noFire = false;
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
    }

    /** Зберегти поточні налаштування як пресет (друзі й маркери в пресет не входять). */
    public static void saveConfig(String name) {
        Data copy = GSON.fromJson(GSON.toJson(d), Data.class);
        copy.configs = new LinkedHashMap<>();
        copy.friends = new ArrayList<>();
        copy.markers = new ArrayList<>();
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
