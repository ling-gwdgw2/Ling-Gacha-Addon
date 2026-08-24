package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.network.ConveneResultPayload;
import com.holysweet.linggacha.network.PullConvenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class GachaRevealScreen extends Screen {

    private final ConveneResultPayload result;
    private int ticksElapsed = 0;
    private boolean soundPlayed = false;

    private Button pullAgainBtn;
    private Button closeBtn;

    public GachaRevealScreen(ConveneResultPayload result) {
        super(Component.literal("Convene Results"));
        this.result = result;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        int btnY = this.height - 32;

        int pullCount = result.prizes().size();
        String againText = pullCount == 10 ? "Convene Again (10x)" : "Convene Again (1x)";

        this.pullAgainBtn = Button.builder(Component.literal(againText), b -> {
            PacketDistributor.sendToServer(new PullConvenePayload(result.bannerId(), pullCount));
            returnToGachaScreen();
        }).bounds(this.width / 2 - 130, btnY, 125, 20).build();

        this.closeBtn = Button.builder(Component.literal("Confirm"), b -> returnToGachaScreen())
                .bounds(this.width / 2 + 5, btnY, 125, 20).build();

        this.addRenderableWidget(pullAgainBtn);
        this.addRenderableWidget(closeBtn);
    }

    private void returnToGachaScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new GachaScreen());
        }
    }

    @Override
    public void onClose() {
        super.onClose(); // Standard Minecraft ESC exit behavior (closes screen to world/pause)
    }

    @Override
    public void tick() {
        ticksElapsed++;
        if (!soundPlayed && Minecraft.getInstance().player != null) {
            if (result.highestStars() >= 5) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
            } else if (result.highestStars() == 4) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
            }
            soundPlayed = true;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int maxTicks = getAnimationDurationTicks();
        if (ticksElapsed < maxTicks) {
            ticksElapsed = maxTicks; // Skip animation on click
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getAnimationDurationTicks() {
        com.holysweet.linggacha.client.animation.AnimatedTexture customAnim =
                com.holysweet.linggacha.client.animation.AnimationManager.getPullAnimation(result.highestStars());
        if (customAnim != null) {
            return Math.max(20, (customAnim.getTotalDurationMs() / 50));
        }
        return 25; // Default 1.25s
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Prevent background blur
    }

    @Override
    public void renderMenuBackground(GuiGraphics guiGraphics) {
        // Prevent background blur
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Solid Darkened Sci-Fi Background Overlay
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF080812);

        int maxTicks = getAnimationDurationTicks();

        // Custom Pull Animation (.gif / .afma) or Soundwave Frequency Wave Burst
        if (ticksElapsed < maxTicks) {
            boolean customPlayed = com.holysweet.linggacha.client.animation.AnimationManager.renderPullAnimation(
                    guiGraphics, result.highestStars(), 0, 0, this.width, this.height, ticksElapsed * 50L
            );
            if (!customPlayed) {
                renderSoundwaveAnimation(guiGraphics);
            } else {
                String skipText = "Convening Resonance... (Click to Skip)";
                int cx = this.width / 2;
                int cy = this.height - 40;
                guiGraphics.drawString(this.font, skipText, cx - this.font.width(skipText) / 2, cy, 0xFFFFD700, true);
            }
            return;
        }

        // Title Header
        String titleText = result.highestStars() >= 5 ? "5-STAR RESONANCE DISCOVERED!" : "CONVENE RESULTS";
        int titleColor = result.highestStars() >= 5 ? 0xFFFFD700 : (result.highestStars() == 4 ? 0xFFC77DFF : 0xFF4CC9F0);
        guiGraphics.drawString(this.font, titleText, this.width / 2 - this.font.width(titleText) / 2, 20, titleColor, true);

        List<ConveneResultPayload.PrizeData> prizes = result.prizes();

        if (prizes.size() == 1) {
            // Single 1x Card Reveal
            renderSinglePrize(guiGraphics, prizes.get(0));
        } else {
            // 10x Grid Reveal (2 rows of 5)
            renderTenPrizes(guiGraphics, prizes);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSoundwaveAnimation(GuiGraphics guiGraphics) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int waveColor = result.highestStars() >= 5 ? 0xFFFFB703 : (result.highestStars() == 4 ? 0xFF9D4EDD : 0xFF4361EE);

        // Draw pulsating frequency bars (Soundwave Spectrogram)
        int numBars = 32;
        int barWidth = 6;
        int spacing = 10;
        int startX = cx - (numBars * spacing) / 2;

        float progress = (float) ticksElapsed / 25.0F;

        for (int i = 0; i < numBars; i++) {
            double angle = (i * 0.4) + (ticksElapsed * 0.3);
            int barHeight = (int) (Math.abs(Math.sin(angle)) * 60 * (1.0F - progress * 0.3F) + 15);

            int x = startX + i * spacing;
            int y1 = cy - barHeight / 2;
            int y2 = cy + barHeight / 2;

            guiGraphics.fill(x, y1, x + barWidth, y2, waveColor);
        }

        String skipText = "Analyzing Frequency Waves... (Click to Skip)";
        guiGraphics.drawString(this.font, skipText, cx - this.font.width(skipText) / 2, cy + 60, 0xFFAAAAAA, true);
    }

    private void renderSinglePrize(GuiGraphics guiGraphics, ConveneResultPayload.PrizeData prize) {
        int cardW = 150;
        int cardH = 190;
        int cardX = this.width / 2 - cardW / 2;
        int cardY = this.height / 2 - cardH / 2 - 10;

        int borderColor = getRarityColor(prize.stars());
        int bgColor = getRarityBg(prize.stars());

        // Card Border & Background
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, borderColor);
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + cardH - 2, bgColor);

        // Render Item
        ItemStack stack = getItemStack(prize.itemId(), prize.count());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cardX + cardW / 2 - 24, cardY + 30, 0);
        guiGraphics.pose().scale(3.0F, 3.0F, 3.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();

        // Stars Display
        String stars = getStarsString(prize.stars());
        guiGraphics.drawString(this.font, stars, cardX + cardW / 2 - this.font.width(stars) / 2, cardY + 105, borderColor, true);

        // Item Name & Count
        String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
        if (prize.count() > 1) name = prize.count() + "x " + name;
        guiGraphics.drawString(this.font, name, cardX + cardW / 2 - this.font.width(name) / 2, cardY + 125, 0xFFFFFFFF, true);
    }

    private void renderTenPrizes(GuiGraphics guiGraphics, List<ConveneResultPayload.PrizeData> prizes) {
        int cardW = 68;
        int cardH = 88;
        int gapX = 10;
        int gapY = 10;

        int totalW = 5 * cardW + 4 * gapX;
        int startX = this.width / 2 - totalW / 2;
        int startY = this.height / 2 - cardH - 10;

        for (int i = 0; i < prizes.size() && i < 10; i++) {
            ConveneResultPayload.PrizeData prize = prizes.get(i);
            int col = i % 5;
            int row = i / 5;

            int x = startX + col * (cardW + gapX);
            int y = startY + row * (cardH + gapY);

            int borderColor = getRarityColor(prize.stars());
            int bgColor = getRarityBg(prize.stars());

            guiGraphics.fill(x, y, x + cardW, y + cardH, borderColor);
            guiGraphics.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, bgColor);

            ItemStack stack = getItemStack(prize.itemId(), prize.count());
            guiGraphics.renderItem(stack, x + cardW / 2 - 8, y + 12);
            guiGraphics.renderItemDecorations(this.font, stack, x + cardW / 2 - 8, y + 12);

            String stars = getStarsString(prize.stars());
            guiGraphics.drawString(this.font, stars, x + cardW / 2 - this.font.width(stars) / 2, y + 42, borderColor, true);

            String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
            if (this.font.width(name) > cardW - 4) {
                name = name.substring(0, Math.min(name.length(), 8)) + "..";
            }
            guiGraphics.drawString(this.font, name, x + cardW / 2 - this.font.width(name) / 2, y + 58, 0xFFFFFFFF, true);

            if (prize.count() > 1) {
                String countStr = "x" + prize.count();
                guiGraphics.drawString(this.font, countStr, x + cardW / 2 - this.font.width(countStr) / 2, y + 72, 0xFFAAAAAA, true);
            }
        }
    }

    private int getRarityColor(int stars) {
        if (stars >= 5) return 0xFFFFB703;
        if (stars == 4) return 0xFF9D4EDD;
        return 0xFF4361EE;
    }

    private int getRarityBg(int stars) {
        if (stars >= 5) return 0xFF352A12;
        if (stars == 4) return 0xFF2A163B;
        return 0xFF141C36;
    }

    private String getStarsString(int stars) {
        if (stars >= 5) return "5-Star";
        if (stars == 4) return "4-Star";
        return "3-Star";
    }

    private ItemStack getItemStack(String itemId, int count) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
            if (itemOpt.isPresent()) {
                return new ItemStack(itemOpt.get(), Math.max(1, count));
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.DIRT, 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
