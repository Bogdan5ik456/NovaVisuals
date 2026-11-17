package com.novavisuals.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Лого NV перед ніком гравців, які грають із Nova Visuals (над головою й у списку Tab).
 * Лого — це символ \uE000 зі шрифту novavisuals:icons (assets/novavisuals/font/icons.json).
 * Хто саме з Nova Visuals, береться з сайту: мод раз на 10 с шле «я онлайн» і питає, кого із цього сервера знає сайт.
 * Дані для зв'язку лаунчер кладе в config/novavisuals-tag.json (після входу через сайт).
 */
public class NovaTag implements ClientModInitializer {
    private static final Style ICON = Style.EMPTY.withFont(new ResourceLocation("novavisuals", "icons"));
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private static volatile Set<String> nova = Set.of();
    private static volatile String url = null, key = null, token = null, self = null;
    private static volatile boolean busy = false;
    private static int ticks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(NovaTag::tick);
    }

    private static void loadCfg() {
        url = null; key = null; token = null; self = null;
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("novavisuals-tag.json");
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            String u = o.get("url").getAsString().replaceAll("/+$", "");
            if (!u.startsWith("https://")) return;
            url = u; key = o.get("key").getAsString(); token = o.get("token").getAsString();
            self = o.has("user") ? o.get("user").getAsString() : null;
        } catch (Exception ignored) {
        }
    }

    private static void tick(Minecraft mc) {
        if (++ticks % 200 != 0) return;   // раз на 10 секунд
        if (mc.getConnection() == null) { nova = Set.of(); return; }
        loadCfg();
        if (token == null) { nova = Set.of(); return; }
        List<String> names = new ArrayList<>();
        for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
            if (names.size() >= 200) break;
            String n = p.getProfile().getName();
            if (n != null && !n.isEmpty()) names.add(n);
        }
        if (busy) return;
        busy = true;
        final String u = url, k = key, t = token, me = self;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject hb = new JsonObject();
                hb.addProperty("p_token", t);
                rpc(u, k, "client_heartbeat", hb);
                JsonArray arr = new JsonArray();
                names.forEach(arr::add);
                JsonObject q = new JsonObject();
                q.add("p_nicks", arr);
                JsonElement r = rpc(u, k, "nova_users", q);
                Set<String> s = new HashSet<>();
                if (me != null) s.add(me.toLowerCase(Locale.ROOT));
                if (r != null && r.isJsonArray()) {
                    for (JsonElement e : r.getAsJsonArray()) {
                        if (e.isJsonObject() && e.getAsJsonObject().has("nick") && !e.getAsJsonObject().get("nick").isJsonNull())
                            s.add(e.getAsJsonObject().get("nick").getAsString().toLowerCase(Locale.ROOT));
                    }
                }
                nova = s;
            } catch (Exception ignored) {
            } finally {
                busy = false;
            }
        });
    }

    private static JsonElement rpc(String url, String key, String fn, JsonObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/rest/v1/rpc/" + fn))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("apikey", key);
        if (key.startsWith("eyJ")) b.header("Authorization", "Bearer " + key);
        HttpResponse<String> r = HTTP.send(b.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) return null;
        String text = r.body();
        return text == null || text.isEmpty() ? null : JsonParser.parseString(text);
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }

    /** Чи є в тексті (ніку, можливо з префіксом команди) нік гравця з Nova Visuals як окреме слово. */
    private static boolean matches(String text) {
        Set<String> set = nova;
        if (set.isEmpty() || text == null) return false;
        String s = text.toLowerCase(Locale.ROOT);
        for (String n : set) {
            int i = s.indexOf(n);
            while (i >= 0) {
                boolean left = i == 0 || !isWordChar(s.charAt(i - 1));
                int e = i + n.length();
                boolean right = e >= s.length() || !isWordChar(s.charAt(e));
                if (left && right) return true;
                i = s.indexOf(n, i + 1);
            }
        }
        return false;
    }

    /** Додає лого перед ім'ям, якщо це гравець з Nova Visuals. Інакше повертає ім'я без змін. */
    public static Component decorate(Component name) {
        try {
            if (name == null || !matches(name.getString())) return name;
            String plain = name.getString();
            if (plain.indexOf('\uE000') >= 0) return name;   // лого вже є
            MutableComponent root = new TextComponent("");
            root.append(new TextComponent("\uE000").setStyle(ICON));
            root.append(new TextComponent(" "));
            root.append(name);
            return root;
        } catch (Throwable t) {
            return name;
        }
    }
}
