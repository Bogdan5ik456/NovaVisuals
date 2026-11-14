package com.novavisuals.fabric121;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Nova Visuals для Minecraft 1.21.11: меню на правий Shift, HUD, гравці поруч, друзі, команди.
 * /nova fps | coords | health | armor | announce  — вмикають і вимикають пункти.
 */
public final class NovaFabric121 implements ClientModInitializer {
    private static int helloTicks = -1;
    private static boolean shiftSeen, shiftPrev;

    @Override
    public void onInitializeClient() {
        Settings.load();
        NovaNet.init();
        HudRenderCallback.EVENT.register(Hud::render);
        HudRenderCallback.EVENT.register((g, t) -> { if (MenuScreen.Keys.rightShift()) shiftSeen = true; });   // ловимо навіть короткий тап

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("nova")
                        .then(toggle("fps", () -> Settings.d.fps, v -> Settings.d.fps = v))
                        .then(toggle("coords", () -> Settings.d.coords, v -> Settings.d.coords = v))
                        .then(toggle("health", () -> Settings.d.health, v -> Settings.d.health = v))
                        .then(toggle("armor", () -> Settings.d.armor, v -> Settings.d.armor = v))
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NovaNet.onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(NovaFabric121::tick);
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

    /** Через ~3 секунди після входу кажемо плагіну NovaVisualsTag, що в нас Nova Visuals (мітка NV). */
    private static int mode(FabricClientCommandSource src, int m) {
        Settings.d.radarMode = m; Settings.d.radarOn = true; Settings.save();
        src.sendFeedback(Component.literal("[Nova] Гравці поруч: " + Settings.RADAR_MODES[m]));
        return 1;
    }

    private static int friendCmd(FabricClientCommandSource src, String act, String name) {
        NovaNet.action(act, name, msg -> src.sendFeedback(Component.literal("[Nova] " + msg)));
        return 1;
    }

    private static void tick(Minecraft mc) {
        NovaNet.tick(mc);
        boolean down = shiftSeen || MenuScreen.Keys.rightShift();   // Right Shift відкриває меню
        shiftSeen = false;
        if (down && !shiftPrev && mc.screen == null && mc.player != null) MenuScreen.open();
        shiftPrev = down;
        if (helloTicks > 0 && --helloTicks == 0 && Settings.d.announce) {
            try {
                if (mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild("nvhello") != null) {
                    mc.getConnection().sendCommand("nvhello");
                }
            } catch (Exception ignored) { }
        }
    }
}
