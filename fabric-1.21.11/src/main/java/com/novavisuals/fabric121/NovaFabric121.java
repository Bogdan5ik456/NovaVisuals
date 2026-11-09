package com.novavisuals.fabric121;

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
 * Nova Visuals для Minecraft 1.21.11 (перша версія): HUD і клієнтські команди.
 * /nova fps | coords | health | armor | announce  — вмикають і вимикають пункти.
 */
public final class NovaFabric121 implements ClientModInitializer {
    private static int helloTicks = -1;

    @Override
    public void onInitializeClient() {
        Settings.load();
        HudRenderCallback.EVENT.register(Hud::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("nova")
                        .then(toggle("fps", () -> Settings.d.fps, v -> Settings.d.fps = v))
                        .then(toggle("coords", () -> Settings.d.coords, v -> Settings.d.coords = v))
                        .then(toggle("health", () -> Settings.d.health, v -> Settings.d.health = v))
                        .then(toggle("armor", () -> Settings.d.armor, v -> Settings.d.armor = v))
                        .then(toggle("announce", () -> Settings.d.announce, v -> Settings.d.announce = v))));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> helloTicks = 60);
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
    private static void tick(Minecraft mc) {
        if (helloTicks > 0 && --helloTicks == 0 && Settings.d.announce) {
            try {
                if (mc.getConnection() != null && mc.getConnection().getCommands().getRoot().getChild("nvhello") != null) {
                    mc.getConnection().sendCommand("nvhello");
                }
            } catch (Exception ignored) { }
        }
    }
}
