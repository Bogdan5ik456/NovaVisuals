package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaTag;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class TabListMixin {
    /** Лого NV перед ніком у списку гравців (Tab). */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nv$icon(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NovaTag.decorate(cir.getReturnValue()));
    }
}
