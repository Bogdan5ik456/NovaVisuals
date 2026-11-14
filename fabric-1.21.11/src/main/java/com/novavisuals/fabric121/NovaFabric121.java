package com.novavisuals.fabric121;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Nova Visuals для Minecraft 1.21.11: меню на правий Shift, віджети HUD, гравці поруч, друзі через сайт,
 * ефекти удару й вбивства, бінди, маркери, пресети. Команди: /nova ...
 */
public final class NovaFabric121 implements ClientModInitializer {
    /** Остання ціль, яку ти вдарив (для віджета Target). */
    public static volatile LivingEntity lastHit;
    public static volatile long lastHitTime;

    private static final Map<Settings.Bind, Boolean> held = new IdentityHashMap<>();
    private static final class Hit { LivingEntity e; int ticks; }
    private static final List<Hit> hits = new ArrayList<>();
    private static LivingEntity hlTarget;
    private static int helloTicks = -1, hlTicks, glowTick;
    private static long lastHitStamp;
    private static boolean shiftSeen, shiftPrev, vPrev, wasWater;
    private static double lastVy;
    private static Supplier<Screen> pending;

    @Override
    public void onInitializeClient() {
        Settings.load();
        NovaNet.init();
        HudRenderCallback.EVENT.register(Hud::render);
        HudRenderCallback.EVENT.register((g, t) -> { if (Keys.rightShift()) shiftSeen = true; });   // ловимо навіть короткий тап

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("nova")
                        .then(ClientCommandManager.literal("menu").executes(c -> { pending = () -> new MenuScreen(0); return 1; }))
                        .then(ClientCommandManager.literal("edit").executes(c -> { pending = () -> new HudEditScreen(0); return 1; }))
                        .then(toggle("fps", () -> Settings.d.showFps, v -> Settings.d.showFps = v))
                        .then(toggle("coords", () -> Settings.d.showCoords, v -> Settings.d.showCoords = v))
                        .then(toggle("health", () -> Settings.d.showHealth, v -> Settings.d.showHealth = v))
                        .then(toggle("armor", () -> Settings.d.showArmor, v -> Settings.d.showArmor = v))
                        .then(toggle("potions", () -> Settings.d.potionsOn, v -> Settings.d.potionsOn = v))
                        .then(toggle("announce", () -> Settings.d.announce, v -> Settings.d.announce = v))
                        .then(toggle("radar", () -> Settings.d.radarOn, v -> Settings.d.radarOn = v))
                        .then(toggle("friendsonly", () -> Settings.d.radarFriendsOnly, v -> Settings.d.radarFriendsOnly = v))
                        .then(ClientCommandManager.literal("mode")
                                .then(ClientCommandManager.literal("list").executes(c -> mode(c.getSource(), 0)))
                                .then(ClientCommandManager.literal("map").executes(c -> mode(c.getSource(), 1)))
                                .then(ClientCommandManager.literal("tags").executes(c -> mode(c.getSource(), 2))))
                        .then(ClientCommandManager.literal("range").executes(c -> {
                            Settings.d.radarRange = (Settings.d.radarRange + 1) % Settings.RADAR_RANGES.length;
                            Settings.save();
                            c.getSource().sendFeedback(Component.literal("[Nova] Дальність: " + Settings.RADAR_RANGES[Settings.d.radarRange] + "м"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("friends").executes(c -> {
                            NovaNet.refresh();
                            if (!NovaNet.linked()) { c.getSource().sendFeedback(Component.literal("[Nova] Не підключено: лаунчер → Профіль → Увійти через сайт")); return 1; }
                            c.getSource().sendFeedback(Component.literal("[Nova] Друзі (" + NovaNet.user + "):"));
                            for (NovaNet.Friend f : NovaNet.friends)
                                c.getSource().sendFeedback(Component.literal((f.online ? " + " : " - ") + f.name + (f.online ? (f.inGame ? " (у грі" + (f.server.isEmpty() ? "" : ", " + f.server) + ")" : " (лаунчер)") : " (офлайн)")));
                            for (String n : NovaNet.incoming) c.getSource().sendFeedback(Component.literal(" ? " + n + ": /nova accept " + n));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("add").then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(c -> friendCmd(c.getSource(), "add", StringArgumentType.getString(c, "name")))))
                        .then(ClientCommandManager.literal("accept").then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(c -> friendCmd(c.getSource(), "accept", StringArgumentType.getString(c, "name")))))
                        .then(ClientCommandManager.literal("remove").then(ClientCommandManager.argument("name", StringArgumentType.word())
                                .executes(c -> friendCmd(c.getSource(), "remove", StringArgumentType.getString(c, "name")))))));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { helloTicks = 60; NovaNet.onJoin(client); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { NovaNet.onLeave(); Radar.clear(); hits.clear(); });
        ClientTickEvents.END_CLIENT_TICK.register(NovaFabric121::tick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (world == mc.level && entity instanceof LivingEntity le) registerHit(mc, le);
            return InteractionResult.PASS;
        });

        // авто-/tpaccept від друзів (за повідомленнями сервера)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            try { onGameMessage(message.getString()); } catch (Exception ignored) { }
            return true;
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        return ClientCommandManager.literal(name).executes(ctx -> {
            boolean now = !get.get();
            set.accept(now);
            Settings.save();
            ctx.getSource().sendFeedback(Component.literal("[Nova] " + name + ": " + (now ? "ON" : "OFF")));
            return 1;
        });
    }

    private static int mode(FabricClientCommandSource src, int m) {
        Settings.d.radarMode = m; Settings.d.radarOn = true; Settings.save();
        src.sendFeedback(Component.literal("[Nova] Гравці поруч: " + Settings.RADAR_MODES[m]));
        return 1;
    }

    private static int friendCmd(FabricClientCommandSource src, String act, String name) {
        NovaNet.action(act, name, msg -> src.sendFeedback(Component.literal("[Nova] " + msg)));
        return 1;
    }

    private static void onGameMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (!Settings.d.autoTpa || mc.getConnection() == null) return;
        String low = msg.toLowerCase(Locale.ROOT);
        boolean tp = low.contains("tpa") || low.contains("teleport") || low.contains("телепорт");
        boolean rq = low.contains("request") || low.contains("wants") || low.contains("запит") || low.contains("хоче");
        if (!tp || !rq) return;
        for (NovaNet.Friend f : NovaNet.friends) {
            String n = f.mc.isEmpty() ? f.name : f.mc;
            if (low.contains(n.toLowerCase(Locale.ROOT))) { mc.getConnection().sendCommand("tpaccept"); return; }
        }
        for (String f : Settings.d.friends) {
            if (low.contains(f.toLowerCase(Locale.ROOT))) { mc.getConnection().sendCommand("tpaccept"); return; }
        }
    }

    private static void registerHit(Minecraft mc, LivingEntity le) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (le == lastHit && now - lastHitStamp < 4L) return;
        lastHit = le;
        lastHitTime = now;
        lastHitStamp = now;
        hits.removeIf(x -> x.e == le);
        Hit h = new Hit();
        h.e = le;
        h.ticks = 120;
        hits.add(h);
        if (hits.size() > 16) hits.remove(0);
        if (Settings.d.hitFx) Fx.hit(mc, le);
        if (Settings.d.hitHighlight) { hlTarget = le; hlTicks = 25; }
    }

    private static void tick(Minecraft mc) {
        NovaNet.tick(mc);
        if (pending != null && mc.screen == null) { Screen s = pending.get(); pending = null; mc.setScreen(s); }

        // ефект вбивства: чекаємо, поки помруть цілі, яких ти вдарив (моби й гравці)
        if (mc.level == null) hits.clear();
        for (Iterator<Hit> it = hits.iterator(); it.hasNext();) {
            Hit h = it.next();
            if (--h.ticks <= 0) { it.remove(); continue; }
            if (h.e.isDeadOrDying() || (h.e.isRemoved() && h.e.deathTime > 0)) {
                if (Settings.d.killFx) Fx.kill(mc, h.e);
                it.remove();
            }
        }
        if (Settings.d.killFx && Settings.d.killAny && mc.level != null && mc.player != null) {
            for (Entity en : mc.level.entitiesForRendering()) {
                if (en instanceof LivingEntity le && le != mc.player && le.deathTime == 1 && le.distanceToSqr(mc.player) < 576.0) Fx.kill(mc, le);
            }
        }
        Fx.tick(mc);

        if (mc.player != null && mc.level != null) {   // бризки при стрибку у воду
            boolean w = mc.player.isInWater();
            if (w && !wasWater && Settings.d.splashFx) Fx.splash(mc, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Math.abs(lastVy));
            wasWater = w;
            lastVy = mc.player.getDeltaMovement().y;
        }
        if (hlTarget != null) {
            if (mc.level == null || --hlTicks <= 0 || !hlTarget.isAlive()) hlTarget = null;
            else Fx.ring(mc, hlTarget, 25 - hlTicks);
        }
        if (Settings.d.itemGlow && ++glowTick % 4 == 0) Fx.itemGlow(mc);

        // Right Shift відкриває меню
        boolean down = shiftSeen || Keys.rightShift();
        shiftSeen = false;
        if (down && !shiftPrev && mc.screen == null && mc.player != null) MenuScreen.open();
        shiftPrev = down;

        // V: вимк. -> список -> карта -> картки
        boolean v = Keys.down(GLFW.GLFW_KEY_V);
        if (v && !vPrev && mc.screen == null && mc.player != null) {
            Settings.Data d = Settings.d;
            if (!d.radarOn) { d.radarOn = true; d.radarMode = 0; }
            else if (d.radarMode < 2) d.radarMode++;
            else d.radarOn = false;
            Settings.save();
            mc.player.displayClientMessage(Component.literal("[Nova] Гравці поруч: " + (d.radarOn ? Settings.RADAR_MODES[d.radarMode] : "вимк.")), true);
        }
        vPrev = v;

        if (mc.player != null && mc.getConnection() != null) {   // бінди: клавіша -> команда
            for (Settings.Bind b : Settings.d.binds) {
                boolean now = mc.screen == null && Keys.down(b.key);
                boolean was = held.getOrDefault(b, false);
                if (now && !was && b.cmd != null && !b.cmd.isEmpty()) {
                    if (b.cmd.startsWith("/")) mc.getConnection().sendCommand(b.cmd.substring(1));
                    else mc.getConnection().sendChat(b.cmd);
                }
                held.put(b, now);
            }
        }

        if (helloTicks > 0 && --helloTicks == 0 && Settings.d.announce) {
            try {
                if (mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild("nvhello") != null) {
                    mc.getConnection().sendCommand("nvhello");
                }
            } catch (Exception ignored) { }
        }
    }
}
