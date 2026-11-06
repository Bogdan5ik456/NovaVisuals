package com.novavisuals.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.novavisuals.fabric.HitFx;
import com.novavisuals.fabric.Settings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    /** Позиція, розмір та анімація рук і предметів від першої особи. */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void nv$hand(float partial, PoseStack ps, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        Settings.Data d = Settings.d;
        if (d.handOn) {
            ps.translate(d.handXf, d.handYf, d.handZf);
            ps.scale(d.handSizeF, d.handSizeF, d.handSizeF);
        }
        HitFx.handAnim(ps);
    }
}
