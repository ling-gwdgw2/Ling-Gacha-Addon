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
        if (Minecraft.getInstance().screen instanceof GachaScreen screen) {
            // Already open, refresh UI
            screen.refreshData();
        } else {
            Minecraft.getInstance().setScreen(new GachaScreen());
        }
    }

    public static void handleConveneResult(ConveneResultPayload payload) {
        Minecraft.getInstance().setScreen(new GachaRevealScreen(payload));
    }

    public static void handleSyncItemPool(AdminSyncItemPoolPayload payload) {
        if (Minecraft.getInstance().screen instanceof GachaScreen screen) {
            screen.onItemPoolSynced(payload);
        }
    }
}
