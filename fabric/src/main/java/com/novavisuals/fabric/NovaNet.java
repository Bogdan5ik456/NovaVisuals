package com.novavisuals.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Зв'язок із сайтом Nova Visuals: друзі онлайн/офлайн і твій статус.
 * Адресу сайту й токен лаунчер кладе у config/novavisuals-link.json (після входу через сайт).
 * Усі запити йдуть в окремому потоці, гра не підвисає.
 */
public final class NovaNet {
    public static final class Friend {
        public String name = "", mc = "", server = "";
        public boolean online, inGame;
        public long lastSeen;
    }

    public static volatile List<Friend> friends = List.of();
    public static volatile List<String> incoming = List.of(), outgoing = List.of();
    public static volatile String user = null, error = "";
    /** Збільшується після кожного оновлення списку: меню за ним перемальовує рядки. */
    public static volatile int version = 0;

    private static volatile String api = null, token = null;
    private static int ticks = 0;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nova-net");
        t.setDaemon(true);
        return t;
    });

    private static Path linkFile() { return FabricLoader.getInstance().getConfigDir().resolve("novavisuals-link.json"); }

    private static void loadLink() {
        api = null; token = null; user = null;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(linkFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            String a = o.get("api").getAsString().replaceAll("/+$", "");
            if (a.startsWith("http://") || a.startsWith("https://")) { api = a; token = o.get("token").getAsString(); user = o.get("user").getAsString(); }
        } catch (Exception ignored) { }
    }

    public static boolean linked() { return token != null; }

    public static void init() { EX.submit(() -> { loadLink(); fetch(); }); }

    private static JsonObject request(String method, String route, JsonObject body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api + "/api/" + route))
                .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        b = body == null ? b.GET() : b.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        if (r.statusCode() >= 400) throw new IOException(o.has("error") ? o.get("error").getAsString() : "HTTP " + r.statusCode());
        return o;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> l = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) l.add(e.getAsString());
        return l;
    }

    private static void fetch() {
        try {
            if (token == null) { friends = List.of(); incoming = List.of(); outgoing = List.of(); error = ""; version++; return; }
            JsonObject o = request("GET", "friends", null);
            List<Friend> l = new ArrayList<>();
            JsonArray arr = o.getAsJsonArray("friends");
            for (JsonElement e : arr) {
                JsonObject f = e.getAsJsonObject();
                Friend x = new Friend();
                x.name = f.get("name").getAsString();
                x.online = f.get("online").getAsBoolean();
                x.inGame = f.has("inGame") && f.get("inGame").getAsBoolean();
                x.mc = f.has("mc") ? f.get("mc").getAsString() : "";
                x.server = f.has("server") ? f.get("server").getAsString() : "";
                x.lastSeen = f.has("lastSeen") ? f.get("lastSeen").getAsLong() : 0L;
                l.add(x);
            }
            friends = l; incoming = strings(o, "incoming"); outgoing = strings(o, "outgoing"); error = "";
        } catch (Exception e) {
            error = String.valueOf(e.getMessage());
        }
        version++;
    }

    /** Оновити список друзів (у фоні). */
    public static void refresh() { EX.submit(() -> { loadLink(); fetch(); }); }

    private static void presence(String state, String mc, String server) {
        EX.submit(() -> {
            if (token == null) return;
            try {
                JsonObject b = new JsonObject();
                b.addProperty("state", state); b.addProperty("mc", mc); b.addProperty("server", server);
                request("POST", "presence", b);
            } catch (Exception ignored) { }
        });
    }

    private static void sendGame(Minecraft mc) {
        if (mc.player == null) return;
        String srv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";
        presence("game", mc.player.getName().getString(), srv);
    }

    public static void onJoin(Minecraft mc) { refresh(); sendGame(mc); }

    public static void onLeave() { presence("leave", "", ""); }

    /** Викликається щотіку: раз на 10 с шле «я в грі», раз на 15 с оновлює друзів. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        ticks++;
        if (ticks % 200 == 0) sendGame(mc);
        if (ticks % 300 == 0) refresh();
    }

    /** Дії з друзями на сайті: add, accept, decline, remove. Результат (текст) приходить у головному потоці. */
    public static void action(String act, String name, Consumer<String> done) {
        EX.submit(() -> {
            String msg;
            try {
                if (token == null) throw new IOException("Спершу увійди через сайт у лаунчері");
                JsonObject b = new JsonObject();
                b.addProperty("name", name);
                JsonObject r = request("POST", "friends/" + act, b);
                msg = r.has("status") && "friends".equals(r.get("status").getAsString()) ? "Ви тепер друзі"
                        : r.has("status") ? "Запит надіслано" : "";
                fetch();
            } catch (Exception e) {
                msg = String.valueOf(e.getMessage());
            }
            String m = msg;
            if (done != null) Minecraft.getInstance().execute(() -> done.accept(m));
        });
    }

    public static void action(String act, String name) { action(act, name, null); }

    /** Друг із сайту (за ніком на сайті або за ніком у грі) або з ручного списку. */
    public static boolean isFriend(String n) {
        for (Friend f : friends) if (f.name.equalsIgnoreCase(n) || (!f.mc.isEmpty() && f.mc.equalsIgnoreCase(n))) return true;
        for (String f : Settings.d.friends) if (f.equalsIgnoreCase(n)) return true;
        return false;
    }

    private NovaNet() { }
}
