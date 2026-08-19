package com.holysweet.linggacha.client;

import com.holysweet.linggacha.client.screen.GachaRevealScreen;
import com.holysweet.linggacha.client.screen.GachaScreen;
import com.holysweet.linggacha.network.ConveneResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientHooks {

    public static void openGachaScreen() {
        Minecraft.getInstance().setScreen(new GachaScreen());
    }

    public static void handleConveneResult(ConveneResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof GachaScreen) {
            Minecraft.getInstance().setScreen(new GachaRevealScreen(payload));
        } else {
            Minecraft.getInstance().setScreen(new GachaRevealScreen(payload));
        }
    }
}
