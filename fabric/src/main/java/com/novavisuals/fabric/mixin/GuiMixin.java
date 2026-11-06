package com.novavisuals.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.novavisuals.fabric.NovaFabric;
import com.novavisuals.fabric.Settings;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Gui.class)
public class GuiMixin {
    /** Свій приціл замість ванільного. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nv$crosshair(PoseStack ps, CallbackInfo ci) {
        if (Settings.d.crosshair) ci.cancel();
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void nv$chat(ChatType type, Component msg, UUID sender, CallbackInfo ci) {
        if (NovaFabric.onChat(type, msg)) ci.cancel();
    }

    @ModifyVariable(method = "handleChat", at = @At("HEAD"), argsOnly = true)
    private Component nv$time(Component msg) {
        return NovaFabric.stamp(msg);
    }
}
