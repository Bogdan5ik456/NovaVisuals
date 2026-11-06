package com.novavisuals.fabric.mixin;

import com.novavisuals.fabric.NovaTitleScreen;
import com.novavisuals.fabric.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    /** Замінюємо ванільне головне меню на меню Nova Visuals. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen nv$screen(Screen screen) {
        if (Settings.d.customTitle && screen != null && screen.getClass() == TitleScreen.class) return new NovaTitleScreen();
        return screen;
    }
}
