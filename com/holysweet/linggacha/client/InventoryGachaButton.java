package com.holysweet.linggacha.client;

import com.holysweet.linggacha.LingGachaMod;
import com.holysweet.linggacha.network.RequestOpenGachaPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LingGachaMod.MODID, value = Dist.CLIENT)
public class InventoryGachaButton {

    private static final ResourceLocation ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LingGachaMod.MODID, "textures/gui/icon.png");

    public static class GachaMenuButton extends Button {

        public GachaMenuButton(int x, int y, int width, int height, OnPress onPress, Tooltip tooltip) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            setTooltip(tooltip);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            int iconSize = Math.min(this.width - 2, this.height - 2);
            int iconX = this.getX() + (this.width - iconSize) / 2;
            int iconY = this.getY() + (this.height - iconSize) / 2;

            guiGraphics.blit(ICON_TEXTURE, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {

        // 2. Add Gacha Icon Button to Game Pause Menu (ESC Menu)
        if (event.getScreen() instanceof PauseScreen screen) {
            int centerX = screen.width / 2;
            int startY = screen.height / 4;

            int x = centerX + 102 + 4;
            int y = startY + 72;

            GachaMenuButton pauseBtn = new GachaMenuButton(
                    x, y, 20, 20,
                    btn -> PacketDistributor.sendToServer(new RequestOpenGachaPayload()),
                    Tooltip.create(Component.literal("Ling Gacha (เปิดตู้สุ่มกาชา)"))
            );

            event.addListener(pauseBtn);
        }
    }
}
