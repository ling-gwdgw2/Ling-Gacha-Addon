package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.network.ConveneResultPayload;
import com.holysweet.linggacha.network.PullConvenePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Random;

public class GachaRevealScreen extends Screen {

    public enum Phase {
        METEOR_CUTSCENE,  // Phase 1: High-energy falling meteor / resonance cutscene
        SEQUENTIAL_CARD,  // Phase 2: Reveals cards one-by-one with 3D model and fanfare
        SUMMARY_GRID      // Phase 3: The iconic 10-Card showcase grid
    }

    private final ConveneResultPayload result;
    private Phase currentPhase = Phase.METEOR_CUTSCENE;

    private int ticksElapsed = 0;
    private int phaseTicks = 0;
    private int currentRevealIndex = 0;
    private int cardRevealTick = 0;
    private boolean cutsceneSoundPlayed = false;

    // Controls
    private Button skipBtn;
    private Button pullAgainBtn;
    private Button closeBtn;

    // Tooltip storage for summary hover
    private ItemStack hoveredTooltipStack = ItemStack.EMPTY;

    public GachaRevealScreen(ConveneResultPayload result) {
        super(Component.literal("Convene Results"));
        this.result = result;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        int btnY = this.height - 34;

        int pullCount = result.prizes().size();
        String againText = pullCount == 10 ? "Convene Again (10x)" : "Convene Again (1x)";

        this.pullAgainBtn = Button.builder(Component.literal(againText), b -> {
            PacketDistributor.sendToServer(new PullConvenePayload(result.bannerId(), pullCount));
            returnToGachaScreen();
        }).bounds(this.width / 2 - 135, btnY, 130, 22).build();

        this.closeBtn = Button.builder(Component.literal("Confirm"), b -> returnToGachaScreen())
                .bounds(this.width / 2 + 5, btnY, 130, 22).build();

        this.skipBtn = Button.builder(Component.literal("Skip >>"), b -> skipToSummary())
                .bounds(this.width - 76, 10, 64, 20).build();

        this.addRenderableWidget(pullAgainBtn);
        this.addRenderableWidget(closeBtn);
        this.addRenderableWidget(skipBtn);

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        boolean inSummary = (currentPhase == Phase.SUMMARY_GRID);
        if (pullAgainBtn != null) pullAgainBtn.visible = inSummary;
        if (closeBtn != null) closeBtn.visible = inSummary;
        if (skipBtn != null) skipBtn.visible = !inSummary;
    }

    private void returnToGachaScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new GachaScreen());
        }
    }

    private void skipToSummary() {
        this.currentPhase = Phase.SUMMARY_GRID;
        this.phaseTicks = 0;
        updateButtonVisibility();
        playSummarySound();
    }

    private void advanceToNextCard() {
        if (currentRevealIndex + 1 < result.prizes().size()) {
            currentRevealIndex++;
            cardRevealTick = 0;
            playCardRevealSound(result.prizes().get(currentRevealIndex).stars());
        } else {
            skipToSummary();
        }
    }

    @Override
    public void tick() {
        ticksElapsed++;
        phaseTicks++;
        cardRevealTick++;

        if (currentPhase == Phase.METEOR_CUTSCENE) {
            if (!cutsceneSoundPlayed) {
                playCutsceneSound();
                cutsceneSoundPlayed = true;
            }

            int cutsceneDuration = getCutsceneDurationTicks();
            if (phaseTicks >= cutsceneDuration) {
                // Auto-advance to sequential card mode
                this.currentPhase = Phase.SEQUENTIAL_CARD;
                this.phaseTicks = 0;
                this.currentRevealIndex = 0;
                this.cardRevealTick = 0;
                updateButtonVisibility();
                if (!result.prizes().isEmpty()) {
                    playCardRevealSound(result.prizes().get(0).stars());
                }
            }
        }
    }

    private int getCutsceneDurationTicks() {
        com.holysweet.linggacha.client.animation.AnimatedTexture customAnim =
                com.holysweet.linggacha.client.animation.AnimationManager.getPullAnimation(result.highestStars());
        if (customAnim != null) {
            return Math.max(25, (customAnim.getTotalDurationMs() / 50));
        }
        return 50; // 2.5 seconds procedural meteor
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (skipBtn != null && skipBtn.isMouseOver(mouseX, mouseY) && skipBtn.visible) {
                return skipBtn.mouseClicked(mouseX, mouseY, button);
            }

            if (currentPhase == Phase.METEOR_CUTSCENE) {
                // Clicking screen during cutscene advances to sequential reveal
                this.currentPhase = Phase.SEQUENTIAL_CARD;
                this.phaseTicks = 0;
                this.currentRevealIndex = 0;
                this.cardRevealTick = 0;
                updateButtonVisibility();
                if (!result.prizes().isEmpty()) {
                    playCardRevealSound(result.prizes().get(0).stars());
                }
                return true;
            } else if (currentPhase == Phase.SEQUENTIAL_CARD) {
                advanceToNextCard();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 32) { // ENTER or SPACE
            if (currentPhase == Phase.METEOR_CUTSCENE) {
                this.currentPhase = Phase.SEQUENTIAL_CARD;
                this.phaseTicks = 0;
                this.currentRevealIndex = 0;
                this.cardRevealTick = 0;
                updateButtonVisibility();
                if (!result.prizes().isEmpty()) {
                    playCardRevealSound(result.prizes().get(0).stars());
                }
                return true;
            } else if (currentPhase == Phase.SEQUENTIAL_CARD) {
                advanceToNextCard();
                return true;
            } else if (currentPhase == Phase.SUMMARY_GRID) {
                returnToGachaScreen();
                return true;
            }
        } else if (keyCode == 256) { // ESCAPE
            if (currentPhase != Phase.SUMMARY_GRID) {
                skipToSummary();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        hoveredTooltipStack = ItemStack.EMPTY;

        switch (currentPhase) {
            case METEOR_CUTSCENE -> renderMeteorCutscene(guiGraphics, mouseX, mouseY, partialTick);
            case SEQUENTIAL_CARD -> renderSequentialCard(guiGraphics, mouseX, mouseY, partialTick);
            case SUMMARY_GRID -> renderSummaryGrid(guiGraphics, mouseX, mouseY, partialTick);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render Tooltip in Summary mode if hovering on a card
        if (currentPhase == Phase.SUMMARY_GRID && !hoveredTooltipStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredTooltipStack, mouseX, mouseY);
        }
    }

    // =========================================================================
    // PHASE 1: METEOR CUTSCENE
    // =========================================================================

    private void renderMeteorCutscene(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Deep Space Canvas
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF05050D);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF090618, 0xFF03030A);

        // Twinkling procedural starfield
        Random rand = new Random(888L);
        for (int i = 0; i < 48; i++) {
            int sx = rand.nextInt(this.width);
            int sy = rand.nextInt(this.height);
            int starAlpha = 100 + (int) (Math.sin((ticksElapsed * 0.12) + i) * 80);
            int starColor = (Math.max(30, Math.min(255, starAlpha)) << 24) | 0x00E0E6FF;
            guiGraphics.fill(sx, sy, sx + 2, sy + 2, starColor);
        }

        int highestStars = result.highestStars();
        int coreColor = highestStars >= 5 ? 0xFFFFD700 : (highestStars == 4 ? 0xFFC77DFF : 0xFF4CC9F0);
        int auraColor = highestStars >= 5 ? 0xFFFF9E00 : (highestStars == 4 ? 0xFF7209B7 : 0xFF0077B6);

        // Check if custom animated texture exists
        boolean customPlayed = com.holysweet.linggacha.client.animation.AnimationManager.renderPullAnimation(
                guiGraphics, highestStars, 0, 0, this.width, this.height, phaseTicks * 50L
        );

        if (!customPlayed) {
            float progress = Math.min(1.0F, phaseTicks / 46.0F);

            // Procedural Radiant Meteor Comet
            float startX = -60.0F;
            float startY = -60.0F;
            float targetX = this.width * 0.68F;
            float targetY = this.height * 0.62F;

            // Smooth accelerated trajectory
            float ease = (float) Math.sin(progress * Math.PI * 0.5);
            float meteorX = startX + (targetX - startX) * ease;
            float meteorY = startY + (targetY - startY) * ease;

            // 1. Long comet tail
            int trailSteps = 24;
            float dirX = targetX - startX;
            float dirY = targetY - startY;
            float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            float ndx = dirX / (dirLen > 0 ? dirLen : 1.0F);
            float ndy = dirY / (dirLen > 0 ? dirLen : 1.0F);

            for (int s = trailSteps; s >= 1; s--) {
                float trailDist = s * 8.0F;
                float tx = meteorX - ndx * trailDist;
                float ty = meteorY - ndy * trailDist;
                int alpha = (int) ((1.0F - (float) s / trailSteps) * 170);
                int rad = (int) (14 * (1.0F - (float) s / trailSteps) + 2);
                int color = (alpha << 24) | (auraColor & 0x00FFFFFF);
                guiGraphics.fill((int) tx - rad, (int) ty - rad, (int) tx + rad, (int) ty + rad, color);
            }

            // 2. Glowing Head & Core
            int headGlowRad = 28;
            guiGraphics.fill((int) meteorX - headGlowRad, (int) meteorY - headGlowRad,
                    (int) meteorX + headGlowRad, (int) meteorY + headGlowRad, (0x66 << 24) | (auraColor & 0x00FFFFFF));
            int headInnerRad = 16;
            guiGraphics.fill((int) meteorX - headInnerRad, (int) meteorY - headInnerRad,
                    (int) meteorX + headInnerRad, (int) meteorY + headInnerRad, (0xBB << 24) | (coreColor & 0x00FFFFFF));
            int headCoreRad = 8;
            guiGraphics.fill((int) meteorX - headCoreRad, (int) meteorY - headCoreRad,
                    (int) meteorX + headCoreRad, (int) meteorY + headCoreRad, 0xFFFFFFFF);

            // 3. Shockwave Rings near impact
            if (progress > 0.55F) {
                float impactP = (progress - 0.55F) / 0.45F;
                int ringRad = (int) (impactP * 95);
                int ringAlpha = (int) ((1.0F - impactP) * 190);
                int ringCol = (ringAlpha << 24) | (coreColor & 0x00FFFFFF);
                drawHollowCircle(guiGraphics, (int) targetX, (int) targetY, ringRad, ringCol);
                drawHollowCircle(guiGraphics, (int) targetX, (int) targetY, ringRad / 2, ringCol);
            }

            // 4. Impact Flash Burst
            if (progress >= 0.88F) {
                float flashP = (progress - 0.88F) / 0.12F;
                int flashAlpha = (int) ((1.0F - flashP) * 210);
                guiGraphics.fill(0, 0, this.width, this.height, (flashAlpha << 24) | (coreColor & 0x00FFFFFF));
            }
        }

        // Subtitle & Skip Prompts
        String resonanceText = highestStars >= 5 ? "★ 5-STAR CELESTIAL RESONANCE DETECTED ★" :
                (highestStars == 4 ? "★ 4-STAR RESONANCE DETECTED ★" : "CONVENING RESONANCE FREQUENCIES...");
        guiGraphics.drawString(this.font, resonanceText,
                this.width / 2 - this.font.width(resonanceText) / 2, this.height - 52, coreColor, true);

        String clickPrompt = "Click screen to reveal cards  •  [Skip >>] for all";
        guiGraphics.drawString(this.font, clickPrompt,
                this.width / 2 - this.font.width(clickPrompt) / 2, this.height - 36, 0xFF8888AA, true);
    }

    // =========================================================================
    // PHASE 2: SEQUENTIAL CARD REVEAL
    // =========================================================================

    private void renderSequentialCard(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF06060F);

        if (result.prizes().isEmpty()) {
            skipToSummary();
            return;
        }

        ConveneResultPayload.PrizeData prize = result.prizes().get(currentRevealIndex);
        int stars = prize.stars();
        int borderColor = getRarityColor(stars);
        int bgColor = getRarityBg(stars);

        int cx = this.width / 2;
        int cy = this.height / 2 - 12;

        float animScale = Math.min(1.0F, cardRevealTick / 5.0F);

        // Flash on first 4 ticks of 5-star
        if (stars >= 5 && cardRevealTick < 5) {
            int flashAlpha = (int) ((1.0F - cardRevealTick / 5.0F) * 160);
            guiGraphics.fill(0, 0, this.width, this.height, (flashAlpha << 24) | 0x00FFD700);
        }

        int cardW = 210;
        int cardH = 270;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        // Card Glow Halo
        int halo = stars >= 5 ? 12 : 6;
        guiGraphics.fill(cardX - halo, cardY - halo, cardX + cardW + halo, cardY + cardH + halo,
                (0x33 << 24) | (borderColor & 0x00FFFFFF));

        // Card Border & Background
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, borderColor);
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + cardH - 2, bgColor);

        // High Resonance Header Ribbon
        if (stars >= 5) {
            String bannerTag = "★ 5-STAR LEGENDARY ★";
            guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFFD4AF37);
            guiGraphics.drawString(this.font, bannerTag, cx - this.font.width(bannerTag) / 2, cardY + 5, 0xFF140E00, false);
        } else if (stars == 4) {
            String bannerTag = "★ 4-STAR FEATURED ★";
            guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF8A2BE2);
            guiGraphics.drawString(this.font, bannerTag, cx - this.font.width(bannerTag) / 2, cardY + 5, 0xFFFFFFFF, false);
        }

        // 3D Item Showcase
        ItemStack stack = getItemStack(prize);
        float time = (System.currentTimeMillis() % 3600000L) / 1000.0F;
        float rot = (time * 40.0F) % 360.0F;
        float bob = (float) Math.sin(time * 2.5F) * 4.0F;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(cx, cy - 25 + bob, 150.0F);
        float itemScale = 54.0F * animScale;
        pose.scale(itemScale, -itemScale, itemScale);
        pose.mulPose(Axis.XP.rotationDegrees(15.0F));
        pose.mulPose(Axis.YP.rotationDegrees(rot));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                15728880,
                OverlayTexture.NO_OVERLAY,
                pose,
                guiGraphics.bufferSource(),
                Minecraft.getInstance().level,
                0);
        guiGraphics.flush();
        pose.popPose();

        // Stars Row
        String starStr = getStarsSymbols(stars);
        int starY = cardY + cardH - 85;
        guiGraphics.drawString(this.font, starStr, cx - this.font.width(starStr) / 2, starY, borderColor, true);

        // Item Name
        String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
        int nameY = starY + 16;
        guiGraphics.drawString(this.font, name, cx - this.font.width(name) / 2, nameY, 0xFFFFFFFF, true);

        // Count Pill
        if (prize.count() > 1) {
            String countText = "Amount: x" + prize.count();
            guiGraphics.drawString(this.font, countText, cx - this.font.width(countText) / 2, nameY + 14, 0xFFFFD700, true);
        }

        // Bottom Tracker & Hint
        String tracker = "[ " + (currentRevealIndex + 1) + " / " + result.prizes().size() + " ]";
        guiGraphics.drawString(this.font, tracker, cx - this.font.width(tracker) / 2, this.height - 44, 0xFFFFD700, true);

        String clickNext = "Click anywhere to continue >>";
        guiGraphics.drawString(this.font, clickNext, cx - this.font.width(clickNext) / 2, this.height - 28, 0xFFAAAAAA, true);
    }

    // =========================================================================
    // PHASE 3: RESULTS SHOWCASE GRID
    // =========================================================================

    private void renderSummaryGrid(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF080814);

        // Header Title
        String title = "CONVENE RESULTS";
        int titleX = this.width / 2 - this.font.width(title) / 2;
        guiGraphics.drawString(this.font, title, titleX, 14, 0xFFFFD700, true);
        guiGraphics.fill(titleX - 12, 25, titleX + this.font.width(title) + 12, 26, 0x88FFD700);

        // Corals earned pill
        String coralText = "+ " + result.corals() + " Afterglow Corals Total";
        int coralW = this.font.width(coralText) + 12;
        int coralX = this.width / 2 - coralW / 2;
        guiGraphics.fill(coralX, 30, coralX + coralW, 44, 0xCC2A163B);
        guiGraphics.drawString(this.font, coralText, coralX + 6, 33, 0xFFC77DFF, true);

        List<ConveneResultPayload.PrizeData> prizes = result.prizes();
        if (prizes.size() == 1) {
            renderSingleSummaryCard(guiGraphics, prizes.get(0), mouseX, mouseY);
        } else {
            renderTenSummaryGrid(guiGraphics, prizes, mouseX, mouseY);
        }
    }

    private void renderSingleSummaryCard(GuiGraphics guiGraphics, ConveneResultPayload.PrizeData prize, int mouseX, int mouseY) {
        int cardW = 160;
        int cardH = 200;
        int cardX = this.width / 2 - cardW / 2;
        int cardY = this.height / 2 - cardH / 2 - 8;

        int borderColor = getRarityColor(prize.stars());
        int bgColor = getRarityBg(prize.stars());

        // Card Glow & Body
        guiGraphics.fill(cardX - 4, cardY - 4, cardX + cardW + 4, cardY + cardH + 4, (0x33 << 24) | (borderColor & 0x00FFFFFF));
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, borderColor);
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + cardH - 2, bgColor);

        ItemStack stack = getItemStack(prize);

        // Render Item Icon Large
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cardX + cardW / 2 - 24, cardY + 32, 0);
        guiGraphics.pose().scale(3.0F, 3.0F, 3.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();

        // Stars Display
        String stars = getStarsSymbols(prize.stars());
        guiGraphics.drawString(this.font, stars, cardX + cardW / 2 - this.font.width(stars) / 2, cardY + 115, borderColor, true);

        // Name
        String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
        guiGraphics.drawString(this.font, name, cardX + cardW / 2 - this.font.width(name) / 2, cardY + 135, 0xFFFFFFFF, true);

        if (prize.count() > 1) {
            String countStr = "x" + prize.count();
            guiGraphics.drawString(this.font, countStr, cardX + cardW / 2 - this.font.width(countStr) / 2, cardY + 152, 0xFFFFD700, true);
        }

        // Hover Tooltip Check
        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
            hoveredTooltipStack = stack;
        }
    }

    private void renderTenSummaryGrid(GuiGraphics guiGraphics, List<ConveneResultPayload.PrizeData> prizes, int mouseX, int mouseY) {
        int cardW = 80;
        int cardH = 110;
        int gapX = 12;
        int gapY = 12;

        int totalW = 5 * cardW + 4 * gapX;
        int totalH = 2 * cardH + gapY;
        int startX = this.width / 2 - totalW / 2;
        int startY = this.height / 2 - totalH / 2 - 4;

        for (int i = 0; i < prizes.size() && i < 10; i++) {
            ConveneResultPayload.PrizeData prize = prizes.get(i);
            int col = i % 5;
            int row = i / 5;

            int x = startX + col * (cardW + gapX);
            int y = startY + row * (cardH + gapY);

            int borderColor = getRarityColor(prize.stars());
            int bgColor = getRarityBg(prize.stars());

            boolean hovered = (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH);

            // Glowing Card Background
            if (prize.stars() >= 5) {
                // Golden pulsing shimmer
                guiGraphics.fill(x - 3, y - 3, x + cardW + 3, y + cardH + 3, 0x44FFD700);
            }
            if (hovered) {
                guiGraphics.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, 0xFFFFFFFF);
            }

            guiGraphics.fill(x, y, x + cardW, y + cardH, borderColor);
            guiGraphics.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, bgColor);

            // Render Item Icon in Center
            ItemStack stack = getItemStack(prize);
            guiGraphics.renderItem(stack, x + cardW / 2 - 8, y + 18);
            guiGraphics.renderItemDecorations(this.font, stack, x + cardW / 2 - 8, y + 18);

            // Stars
            String stars = getStarsSymbols(prize.stars());
            guiGraphics.drawString(this.font, stars, x + cardW / 2 - this.font.width(stars) / 2, y + 54, borderColor, true);

            // Truncated Item Name
            String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
            if (this.font.width(name) > cardW - 6) {
                name = name.substring(0, Math.min(name.length(), 7)) + "..";
            }
            guiGraphics.drawString(this.font, name, x + cardW / 2 - this.font.width(name) / 2, y + 72, 0xFFFFFFFF, true);

            // Count badge
            if (prize.count() > 1) {
                String countStr = "x" + prize.count();
                guiGraphics.drawString(this.font, countStr, x + cardW - this.font.width(countStr) - 5, y + 92, 0xFFFFD700, true);
            }

            if (hovered) {
                hoveredTooltipStack = stack;
            }
        }
    }

    // =========================================================================
    // AUDIO & UTILITY HELPERS
    // =========================================================================

    private void playCutsceneSound() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F));
            if (result.highestStars() >= 5) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F));
            }
        }
    }

    private void playCardRevealSound(int stars) {
        if (Minecraft.getInstance().player != null) {
            if (stars >= 5) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
            } else if (stars == 4) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
            }
        }
    }

    private void playSummarySound() {
        if (Minecraft.getInstance().player != null) {
            if (result.highestStars() >= 5) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F));
            } else if (result.highestStars() == 4) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
    }

    private void drawHollowCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        for (int a = 0; a < 360; a += 15) {
            double rad = Math.toRadians(a);
            int px = cx + (int) (Math.cos(rad) * radius);
            int py = cy + (int) (Math.sin(rad) * radius);
            guiGraphics.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    private int getRarityColor(int stars) {
        if (stars >= 5) return 0xFFFFB703;
        if (stars == 4) return 0xFFC77DFF;
        return 0xFF4CC9F0;
    }

    private int getRarityBg(int stars) {
        if (stars >= 5) return 0xFF281E08;
        if (stars == 4) return 0xFF1C1028;
        return 0xFF0D1527;
    }

    private String getStarsSymbols(int stars) {
        return switch (stars) {
            case 5 -> "★★★★★";
            case 4 -> "★★★★";
            default -> "★★★";
        };
    }

    private ItemStack getItemStack(ConveneResultPayload.PrizeData prize) {
        ItemStack stack = ItemStack.EMPTY;
        if (prize.snbt() != null && !prize.snbt().isEmpty() && Minecraft.getInstance().level != null) {
            try {
                var tag = TagParser.parseTag(prize.snbt());
                stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(prize.itemId());
                var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (itemOpt.isPresent()) {
                    stack = new ItemStack(itemOpt.get(), Math.max(1, prize.count()));
                }
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.DIRT, Math.max(1, prize.count()));
        }
        return stack;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
