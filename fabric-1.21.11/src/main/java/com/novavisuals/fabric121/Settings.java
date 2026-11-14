package com.novavisuals.fabric121;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Налаштування мода для 1.21.11: config/novavisuals.json */
public final class Settings {
    public static class Data {
        public boolean fps = true, coords = true, health = true, armor = true, announce = true;
        public boolean radarOn = true, radarFriendsOnly = false;
        public int radarMode = 0, radarRange = 1, radarMax = 1;   // 0 список, 1 карта, 2 картки
    }

    public static final String[] RADAR_MODES = {"list", "map", "tags"};
    public static final int[] RADAR_RANGES = {32, 64, 128, 256};
    public static final int[] RADAR_MAX = {3, 5, 8, 12};

    public static Data d = new Data();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("novavisuals.json"); }

    public static void load() {
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            Data x = GSON.fromJson(r, Data.class);
            if (x != null) d = x;
        } catch (Exception ignored) { }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(d, w);
        } catch (Exception ignored) { }
    }

    private Settings() { }
}
