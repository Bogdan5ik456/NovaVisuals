package com.novavisuals;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("novavisuals")
public class NovaVisualsMod {
    public NovaVisualsMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init();
        }
    }
}
