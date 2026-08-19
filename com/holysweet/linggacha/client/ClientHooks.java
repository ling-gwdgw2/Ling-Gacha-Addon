package com.holysweet.linggacha.client;

import com.holysweet.linggacha.client.screen.GachaRevealScreen;
import com.holysweet.linggacha.client.screen.GachaScreen;
import com.holysweet.linggacha.network.AdminSyncItemPoolPayload;
import com.holysweet.linggacha.network.ConveneResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientHooks {

    public static void openGachaScreen() {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GachaScreen screen) {
                screen.refreshData();
            } else {
                mc.setScreen(new GachaScreen());
            }
        });
    }

    public static void handleConveneResult(ConveneResultPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new GachaRevealScreen(payload));
        });
    }

    public static void handleSyncItemPool(AdminSyncItemPoolPayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof GachaScreen screen) {
                screen.onItemPoolSynced(payload);
            }
        });
    }
}
