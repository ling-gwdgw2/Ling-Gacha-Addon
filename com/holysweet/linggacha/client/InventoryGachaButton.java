package com.holysweet.linggacha.client;

import com.holysweet.linggacha.LingGachaMod;
import com.holysweet.linggacha.network.RequestOpenGachaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

    public static class GachaButton extends Button {
        private final ItemStack tokenStack;

        public GachaButton(int x, int y, OnPress onPress, Tooltip tooltip) {
            super(x, y, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
            setTooltip(tooltip);
            this.tokenStack = (LingGachaMod.CONVENE_TIDE != null && LingGachaMod.CONVENE_TIDE.get() != null)
                    ? new ItemStack(LingGachaMod.CONVENE_TIDE.get())
                    : ItemStack.EMPTY;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            // Draw glowing star icon or item stack
            if (!this.tokenStack.isEmpty()) {
                guiGraphics.renderItem(this.tokenStack, this.getX() + 1, this.getY() + 1);
            } else {
                int starColor = this.isHoveredOrFocused() ? 0xFFFFD700 : 0xFFE0AAFF;
                guiGraphics.drawString(Minecraft.getInstance().font, "✦", this.getX() + 5, this.getY() + 5, starColor, true);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
            int x = screen.getGuiLeft() + 128;
            int y = screen.getGuiTop() + 6;

            GachaButton gachaBtn = new GachaButton(
                    x, y,
                    btn -> {
                        PacketDistributor.sendToServer(new RequestOpenGachaPayload());
                    },
                    Tooltip.create(Component.literal("Open Convene / Gacha"))
            );

            event.addListener(gachaBtn);
        }
    }
}
