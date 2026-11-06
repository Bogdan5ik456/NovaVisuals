package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaFabric;
import com.novavisuals.fabric.Settings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    /** Свій колір неба (Custom Sky): підміняємо колір, який гра використовує для малювання неба. */
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void nv$sky(Vec3 pos, float partial, CallbackInfoReturnable<Vec3> cir) {
        if (!Settings.d.fogOn) return;
        float[] c = NovaFabric.skyRgb();
        cir.setReturnValue(new Vec3(c[0], c[1], c[2]));
    }
}
