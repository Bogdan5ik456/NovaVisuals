package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaFabric;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** Зум: ділимо кут огляду на поточне наближення. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nv$fov(Camera camera, float partial, boolean useConfigured, CallbackInfoReturnable<Double> cir) {
        double z = NovaFabric.zoomCur;
        if (z > 1.001) cir.setReturnValue(cir.getReturnValue() / z);
    }
}
