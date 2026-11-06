package com.novavisuals.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.novavisuals.fabric.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    /** Без вогню на екрані. */
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void nv$fire(Minecraft mc, PoseStack ps, CallbackInfo ci) {
        if (Settings.d.noFire) ci.cancel();
    }
}
