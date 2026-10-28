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
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;
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

    private static final java.util.Map<Settings.Bind, Boolean> held = new java.util.IdentityHashMap<>();
    private static int helloTicks = -1;
    private static double zoomCur = 1.0;
    private static double gammaSaved = -1;

    public static void init() {
        Settings.load();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientInit::setup);
        modBus.addListener(Cosmetics::registerLayers);
        modBus.addListener(Cosmetics::addLayers);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::loggedIn);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::renderTick);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fov);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fog);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::chat);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::novaChat);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::chatTime);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::fire);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::hand);
        MinecraftForge.EVENT_BUS.addListener(ClientInit::screenOpen);
    }

    private static void setup(FMLClientSetupEvent e) {
        ClientRegistry.registerKeyBinding(MENU);
        ClientRegistry.registerKeyBinding(ZOOM);
        OverlayRegistry.registerOverlayTop("Nova Visuals", Hud::render);
    }

    /** Власний час доби: виставляємо перед кожним кадром, щоб небо не мерехтіло. */
    private static void applyTime() {
        Minecraft mc = Minecraft.getInstance();
        if (Settings.d.timeOn && mc.level != null) mc.level.setDayTime(Settings.TIMES[Settings.d.timeIdx]);
    }

    private static void renderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) applyTime();
    }

    /** Після входу на сервер (через ~3 с) надсилаємо плагіну NovaVisualsTag службове «привіт». */
    private static void loggedIn(ClientPlayerNetworkEvent.LoggedInEvent e) {
        helloTicks = 60;
    }

    private static void sendHello(Minecraft mc) {
        try {
            if (mc.getConnection() == null) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf("0.5.5");
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new ResourceLocation("novavisuals", "hello"), buf));
        } catch (Exception ignored) { }
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) { applyTime(); return; }
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (helloTicks > 0 && --helloTicks == 0 && Settings.d.announce) sendHello(mc);

        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.level != null) mc.setScreen(new MenuScreen());
        }

        // бінди: клавіша -> команда чи повідомлення в чат
        if (mc.player != null) {
            for (Settings.Bind b : Settings.d.binds) {
                boolean now = mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), b.key);
                boolean was = held.getOrDefault(b, false);
                if (now && !was) mc.player.chat(b.cmd);
                held.put(b, now);
            }
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
        applyTime();
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

    /** Запити в друзі між гравцями з модом: приходять приватним повідомленням, а приймаються в меню (Right Shift -> Friends). */
    private static void novaChat(ClientChatReceivedEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String msg = e.getMessage().getString();
        String me = mc.player.getName().getString();
        java.util.regex.Matcher req = java.util.regex.Pattern.compile("NV-FRIEND-REQ\\s+(\\w{3,16})").matcher(msg);
        java.util.regex.Matcher ok = java.util.regex.Pattern.compile("NV-FRIEND-OK\\s+(\\w{3,16})").matcher(msg);
        if (req.find()) {
            e.setCanceled(true);                       // службове повідомлення не показуємо
            String who = req.group(1);
            if (who.equalsIgnoreCase(me)) return;      // це відлуння нашого власного запиту
            if (!Settings.d.friends.contains(who) && !Settings.d.pending.contains(who) && Settings.d.pending.size() < 20) {
                Settings.d.pending.add(who);
                Settings.save();
                mc.gui.getChat().addMessage(new TextComponent("[Nova] Запит у друзі від " + who + ". Правий Shift -> Friends."));
            }
        } else if (ok.find()) {
            e.setCanceled(true);
            String who = ok.group(1);
            if (who.equalsIgnoreCase(me)) return;
            if (!Settings.d.friends.contains(who)) Settings.d.friends.add(who);
            Settings.save();
            mc.gui.getChat().addMessage(new TextComponent("[Nova] " + who + " прийняв твій запит у друзі."));
        }
    }

    private static void chatTime(ClientChatReceivedEvent e) {
        if (!Settings.d.chatTime) return;
        String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        e.setMessage(new TextComponent("[" + t + "] ").append(e.getMessage()));
    }

    /** Позиція й розмір рук та предметів від першої особи (змінюється лише картинка в тебе). */
    private static void hand(RenderHandEvent e) {
        if (!Settings.d.handOn || e.getHand() != InteractionHand.MAIN_HAND) return;
        Settings.Data d = Settings.d;
        e.getPoseStack().translate(Settings.HAND_POS[d.handX], Settings.HAND_POS[d.handY], Settings.HAND_POS[d.handZ]);
        float sc = (float) Settings.HAND_SIZE[d.handSize];
        e.getPoseStack().scale(sc, sc, sc);
    }

    /** Підміна ванільного головного меню на меню Nova Visuals (вимикається в Configs → Main Menu). */
    private static void screenOpen(ScreenOpenEvent e) {
        if (Settings.d.customTitle && e.getScreen() != null && e.getScreen().getClass() == TitleScreen.class) e.setScreen(new NovaTitleScreen());
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
