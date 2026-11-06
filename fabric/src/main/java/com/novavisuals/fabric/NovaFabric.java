package com.novavisuals.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
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

/** Nova Visuals для Fabric 1.18.2: події, клавіші, ефекти. Малювання меню й ефектів спільне з Forge-версією. */
public final class NovaFabric implements ClientModInitializer {
    public static final KeyMapping MENU = new KeyMapping("key.novavisuals.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.novavisuals");
    public static final KeyMapping ZOOM = new KeyMapping("key.novavisuals.zoom",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.novavisuals");

    /** Поточне наближення (читає міксин FOV). */
    public static volatile double zoomCur = 1.0;

    private static final Map<Settings.Bind, Boolean> held = new IdentityHashMap<>();
    private static final class Hit { LivingEntity e; int ticks; }
    private static final List<Hit> hits = new ArrayList<>();
    private static LivingEntity hlTarget, lastHit;
    private static int helloTicks = -1, glintTick, hlTicks, glowTick;
    private static long lastHitTime;
    private static boolean wasWater;
    private static double lastVy, gammaSaved = -1;

    @Override
    public void onInitializeClient() {
        Settings.load();
        KeyBindingHelper.registerKeyBinding(MENU);
        KeyBindingHelper.registerKeyBinding(ZOOM);
        ClientTickEvents.START_CLIENT_TICK.register(mc -> applyTime());
        ClientTickEvents.END_CLIENT_TICK.register(NovaFabric::tick);
        WorldRenderEvents.START.register(ctx -> applyTime());
        HudRenderCallback.EVENT.register(Hud::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> helloTicks = 60);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide && entity instanceof LivingEntity le) registerHit(Minecraft.getInstance(), le);
            return InteractionResult.PASS;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen.getClass() == TitleScreen.class) {
                Screens.getButtons(screen).add(new Button(screen.width - 112, 6, 106, 20, new TextComponent("Nova Visuals"), b -> {
                    Settings.d.customTitle = true;
                    Settings.save();
                    Minecraft.getInstance().setScreen(new NovaTitleScreen());
                }));
            }
        });
        Cosmetics.register();
    }

    private static void applyTime() {
        Minecraft mc = Minecraft.getInstance();
        if (Settings.d.timeOn && mc.level != null) mc.level.setDayTime(Settings.TIMES[Settings.d.timeIdx]);
    }

    private static void registerHit(Minecraft mc, LivingEntity le) {
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (le == lastHit && now - lastHitTime < 4L) return;
        lastHit = le;
        lastHitTime = now;
        hits.removeIf(x -> x.e == le);
        Hit h = new Hit();
        h.e = le;
        h.ticks = 120;
        hits.add(h);
        if (hits.size() > 16) hits.remove(0);
        if (Settings.d.hitFx) Fx.hit(mc, le);
        if (Settings.d.hitHighlight) { hlTarget = le; hlTicks = 25; }
    }

    private static void sendHello(Minecraft mc) {
        try {
            if (mc.getConnection() == null || mc.player == null) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(Settings.VERSION);
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new ResourceLocation("novavisuals", "hello"), buf));
            if (mc.getConnection().getCommands().getRoot().getChild("nvhello") != null) mc.player.chat("/nvhello");
            mc.player.displayClientMessage(new TextComponent("[Nova] NV надіслано серверу"), true);
        } catch (Exception ignored) { }
    }

    private static void tick(Minecraft mc) {
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
        if (++glintTick % 2 == 0) Glint.tick(mc);

        if (hlTarget != null) {
            if (mc.level == null || --hlTicks <= 0 || !hlTarget.isAlive()) hlTarget = null;
            else HitFx.ring(mc, hlTarget, 25 - hlTicks);
        }
        if (Settings.d.itemGlow && ++glowTick % 4 == 0) HitFx.itemGlow(mc);

        if (helloTicks > 0 && --helloTicks == 0 && Settings.d.announce) sendHello(mc);

        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.level != null) mc.setScreen(new MenuScreen());
        }

        if (mc.player != null) {   // бінди: клавіша -> команда
            for (Settings.Bind b : Settings.d.binds) {
                boolean now = mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), b.key);
                boolean was = held.getOrDefault(b, false);
                if (now && !was) mc.player.chat(b.cmd);
                held.put(b, now);
            }
        }

        double target = (Settings.d.zoom && ZOOM.isDown() && mc.screen == null) ? Settings.ZOOMS[Settings.d.zoomLevel] : 1.0;
        zoomCur += (target - zoomCur) * 0.35;
        if (Math.abs(target - zoomCur) < 0.005) zoomCur = target;

        if (Settings.d.fullbright) {
            if (gammaSaved < 0) gammaSaved = mc.options.gamma;
            mc.options.gamma = 15.0;
        } else if (gammaSaved >= 0) {
            mc.options.gamma = gammaSaved;
            gammaSaved = -1;
        }
        applyTime();
    }

    /** Колір неба й туману: або три повзунки (Sky Custom), або готовий варіант. Читають міксини. */
    public static float[] skyRgb() {
        Settings.Data d = Settings.d;
        return d.skyCustom ? new float[]{d.skyR, d.skyG, d.skyB} : Settings.FOG[Math.max(0, Math.min(Settings.FOG.length - 1, d.fogIdx))];
    }

    /** Час перед повідомленням у чаті (викликає міксин). */
    public static Component stamp(Component msg) {
        if (!Settings.d.chatTime) return msg;
        String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return new TextComponent("[" + t + "] ").append(msg);
    }

    /** Чат: запити в друзі між гравцями з модом (ховаємо службові повідомлення) та авто-/tpa від друзів. true = не показувати. */
    public static boolean onChat(ChatType type, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String msg = message.getString();
        String me = mc.player.getName().getString();
        java.util.regex.Matcher req = java.util.regex.Pattern.compile("NV-FRIEND-REQ\\s+(\\w{3,16})").matcher(msg);
        java.util.regex.Matcher ok = java.util.regex.Pattern.compile("NV-FRIEND-OK\\s+(\\w{3,16})").matcher(msg);
        if (req.find()) {
            String who = req.group(1);
            if (!who.equalsIgnoreCase(me) && !Settings.d.friends.contains(who) && !Settings.d.pending.contains(who) && Settings.d.pending.size() < 20) {
                Settings.d.pending.add(who);
                Settings.save();
                mc.gui.getChat().addMessage(new TextComponent("[Nova] Запит у друзі від " + who + ". Правий Shift -> Friends."));
            }
            return true;
        }
        if (ok.find()) {
            String who = ok.group(1);
            if (!who.equalsIgnoreCase(me)) {
                if (!Settings.d.friends.contains(who)) Settings.d.friends.add(who);
                Settings.save();
                mc.gui.getChat().addMessage(new TextComponent("[Nova] " + who + " прийняв твій запит у друзі."));
            }
            return true;
        }
        if (Settings.d.autoTpa && !Settings.d.friends.isEmpty() && type == ChatType.SYSTEM) {
            String low = msg.toLowerCase(Locale.ROOT);
            boolean tp = low.contains("tpa") || low.contains("teleport") || low.contains("телепорт");
            boolean rq = low.contains("request") || low.contains("wants") || low.contains("запит") || low.contains("хоче");
            if (tp && rq) {
                for (String f : Settings.d.friends) {
                    if (low.contains(f.toLowerCase(Locale.ROOT))) { mc.player.chat("/tpaccept"); break; }
                }
            }
        }
        return false;
    }
}
