package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaFabric;
import com.novavisuals.fabric.Settings;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {
    /** Свій колір туману на обрії (Custom Sky): гра бере його з біома. */
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void nv$fog(CallbackInfoReturnable<Integer> cir) {
        if (!Settings.d.fogOn) return;
        float[] c = NovaFabric.fogRgb();
        cir.setReturnValue(((Math.round(c[0] * 255) & 255) << 16) | ((Math.round(c[1] * 255) & 255) << 8) | (Math.round(c[2] * 255) & 255));
    }
}
