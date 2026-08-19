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
            this.onClose();
        }).bounds(this.width / 2 - 130, btnY, 125, 20).build();

        this.closeBtn = Button.builder(Component.literal("Confirm"), b -> this.onClose())
                .bounds(this.width / 2 + 5, btnY, 125, 20).build();

        this.addRenderableWidget(pullAgainBtn);
        this.addRenderableWidget(closeBtn);
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
        if (ticksElapsed < 25) {
            ticksElapsed = 25; // Skip animation on click
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        // Soundwave Frequency Wave Burst Animation (First ~1.25s)
        if (ticksElapsed < 25) {
            renderSoundwaveAnimation(guiGraphics);
            return;
        }

        // Title Header
        String titleText = result.highestStars() >= 5 ? "✦✦✦ 5-STAR RESONANCE DISCOVERED! ✦✦✦" : "CONVENE RESULTS // 唤取结果";
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
        int cardW = 120;
        int cardH = 150;
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
        guiGraphics.pose().translate(cardX + cardW / 2 - 20, cardY + 25, 0);
        guiGraphics.pose().scale(2.5F, 2.5F, 2.5F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();

        // Stars Display
        String stars = getStarsString(prize.stars());
        guiGraphics.drawString(this.font, stars, cardX + cardW / 2 - this.font.width(stars) / 2, cardY + 80, borderColor, true);

        // Item Name & Count
        String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
        if (prize.count() > 1) name = prize.count() + "x " + name;
        guiGraphics.drawString(this.font, name, cardX + cardW / 2 - this.font.width(name) / 2, cardY + 98, 0xFFFFFFFF, true);
    }

    private void renderTenPrizes(GuiGraphics guiGraphics, List<ConveneResultPayload.PrizeData> prizes) {
        int cardW = 58;
        int cardH = 75;
        int gapX = 8;
        int gapY = 8;

        int totalW = 5 * cardW + 4 * gapX;
        int startX = this.width / 2 - totalW / 2;
        int startY = this.height / 2 - cardH - 5;

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
            guiGraphics.renderItem(stack, x + cardW / 2 - 8, y + 10);
            guiGraphics.renderItemDecorations(this.font, stack, x + cardW / 2 - 8, y + 10);

            String stars = getStarsString(prize.stars());
            guiGraphics.drawString(this.font, stars, x + cardW / 2 - this.font.width(stars) / 2, y + 34, borderColor, true);

            String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
            if (this.font.width(name) > cardW - 4) {
                name = name.substring(0, Math.min(name.length(), 7)) + "..";
            }
            guiGraphics.drawString(this.font, name, x + cardW / 2 - this.font.width(name) / 2, y + 48, 0xFFFFFFFF, true);

            if (prize.count() > 1) {
                String countStr = "x" + prize.count();
                guiGraphics.drawString(this.font, countStr, x + cardW / 2 - this.font.width(countStr) / 2, y + 60, 0xFFAAAAAA, true);
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
        if (stars >= 5) return "★★★★★";
        if (stars == 4) return "★★★★";
        return "★★★";
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
