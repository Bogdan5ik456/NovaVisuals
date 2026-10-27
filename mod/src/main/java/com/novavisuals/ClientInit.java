package com.novavisuals;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ChatType;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Реєстрація клавіш, подій і візуальних функцій (тільки клієнт). */
public final class ClientInit {
    public static final KeyMapping MENU = new KeyMapping("key.novavisuals.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.novavisuals");
    public static final KeyMapping ZOOM = new KeyMapping("key.novavisuals.zoom",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.novavisuals");

    private static double zoomCur = 1.0;
    private static double gammaSaved = -1;

    public static void init() {
        Settings.load();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientInit::setup);
        modBus.addListener(Cosmetics::registerLayers);
        modBus.addListener(Cosmetics::addLayers);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fov);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fog);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::chat);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::chatTime);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fire);
    }

    private static void setup(FMLClientSetupEvent e) {
        ClientRegistry.registerKeyBinding(MENU);
        ClientRegistry.registerKeyBinding(ZOOM);
        OverlayRegistry.registerOverlayTop("Nova Visuals", Hud::render);
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.level != null) mc.setScreen(new MenuScreen());
        }

        // плавний зум (утримуй C)
        double target = (Settings.d.zoom && ZOOM.isDown() && mc.screen == null)
                ? Settings.ZOOMS[Settings.d.zoomLevel] : 1.0;
        zoomCur += (target - zoomCur) * 0.35;
        if (Math.abs(target - zoomCur) < 0.005) zoomCur = target;

        // власний приціл замість ванільного
        OverlayRegistry.enableOverlay(ForgeIngameGui.CROSSHAIR_ELEMENT, !Settings.d.crosshair);

        // яскравість
        if (Settings.d.fullbright) {
            if (gammaSaved < 0) gammaSaved = mc.options.gamma;
            mc.options.gamma = 15.0;
        } else if (gammaSaved >= 0) {
            mc.options.gamma = gammaSaved;
            gammaSaved = -1;
        }

        // час доби (тільки в тебе на екрані)
        if (Settings.d.timeOn && mc.level != null) {
            mc.level.setDayTime(Settings.TIMES[Settings.d.timeIdx]);
        }
    }

    private static void fov(EntityViewRenderEvent.FieldOfView e) {
        if (zoomCur > 1.001) e.setFOV(e.getFOV() / zoomCur);
    }

    private static void fog(EntityViewRenderEvent.FogColors e) {
        if (!Settings.d.fogOn) return;
        float[] c = Settings.FOG[Settings.d.fogIdx];
        e.setRed(c[0]);
        e.setGreen(c[1]);
        e.setBlue(c[2]);
    }

    private static void chatTime(ClientChatReceivedEvent e) {
        if (!Settings.d.chatTime) return;
        String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        e.setMessage(new TextComponent("[" + t + "] ").append(e.getMessage()));
    }

    private static void fire(RenderBlockOverlayEvent e) {
        if (Settings.d.noFire && e.getOverlayType() == RenderBlockOverlayEvent.OverlayType.FIRE) e.setCanceled(true);
    }

    /** Авто-прийняття /tpa: тільки від друзів і тільки на системні повідомлення сервера (не на чат гравців). */
    private static void chat(ClientChatReceivedEvent e) {
        if (!Settings.d.autoTpa || Settings.d.friends.isEmpty()) return;
        if (e.getType() != ChatType.SYSTEM) return;
        String msg = e.getMessage().getString().toLowerCase(Locale.ROOT);
        boolean tp = msg.contains("tpa") || msg.contains("teleport") || msg.contains("телепорт");
        boolean req = msg.contains("request") || msg.contains("wants") || msg.contains("запит") || msg.contains("хоче");
        if (!tp || !req) return;
        for (String f : Settings.d.friends) {
            if (msg.contains(f.toLowerCase(Locale.ROOT))) {
                LocalPlayer p = Minecraft.getInstance().player;
                if (p != null) p.chat("/tpaccept");
                return;
            }
        }
    }

    private ClientInit() { }
}
