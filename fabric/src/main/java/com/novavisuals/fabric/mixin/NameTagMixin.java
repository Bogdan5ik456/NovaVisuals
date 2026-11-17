package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaTag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public class NameTagMixin {
    /** Лого NV перед ніком над головою гравця. */
    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component nv$icon(Component name) {
        return NovaTag.decorate(name);
    }
}
