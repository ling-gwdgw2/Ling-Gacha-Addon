package com.holysweet.linggacha.client;

import com.holysweet.linggacha.LingGachaMod;
import com.holysweet.linggacha.network.RequestOpenGachaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LingGachaMod.MODID, value = Dist.CLIENT)
public class InventoryGachaButton {

    public static class GachaMenuButton extends Button {
        private final ItemStack tokenStack;

        public GachaMenuButton(int x, int y, OnPress onPress, Tooltip tooltip) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            setTooltip(tooltip);
            this.tokenStack = (LingGachaMod.CONVENE_TIDE != null && LingGachaMod.CONVENE_TIDE.get() != null)
                    ? new ItemStack(LingGachaMod.CONVENE_TIDE.get())
                    : ItemStack.EMPTY;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            if (!this.tokenStack.isEmpty()) {
                guiGraphics.renderItem(this.tokenStack, this.getX() + 2, this.getY() + 2);
            } else {
                int starColor = this.isHoveredOrFocused() ? 0xFFFFD700 : 0xFFE0AAFF;
                guiGraphics.drawString(Minecraft.getInstance().font, "✦", this.getX() + 6, this.getY() + 6, starColor, true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Place Gacha button in the PauseScreen (Game Menu / ESC Menu)
        if (event.getScreen() instanceof PauseScreen screen) {
            int centerX = screen.width / 2;
            int startY = screen.height / 4;

            // Positioned symmetrically on the right side of the menu (Right of 'Report Bugs' button, above 's' button)
            int x = centerX + 102 + 4;
            int y = startY + 72;

            GachaMenuButton gachaBtn = new GachaMenuButton(
                    x, y,
                    btn -> {
                        PacketDistributor.sendToServer(new RequestOpenGachaPayload());
                    },
                    Tooltip.create(Component.literal("Convene / Gacha (唤取)"))
            );

            event.addListener(gachaBtn);
        }
    }
}
