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
        METEOR_CUTSCENE,  // Phase 1: High-energy falling meteor / celestial resonance cutscene
        SEQUENTIAL_CARD,  // Phase 2: Reveals cards one-by-one with 3D model, sunburst rays, and fanfare
        SUMMARY_GRID      // Phase 3: The iconic 10-Card showcase grid with breathing aura & tooltips
    }

    private final ConveneResultPayload result;
    private Phase currentPhase = Phase.METEOR_CUTSCENE;

    private int ticksElapsed = 0;
    private int phaseTicks = 0;
    private int currentRevealIndex = 0;
    private int cardRevealTick = 0;
    private boolean cutsceneSoundPlayed = false;
    private boolean impactSoundPlayed = false;

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
        String againText = pullCount == 10 ? "✦ Convene Again (10x)" : "✦ Convene Again (1x)";

        this.pullAgainBtn = Button.builder(Component.literal(againText), b -> {
            PacketDistributor.sendToServer(new PullConvenePayload(result.bannerId(), pullCount));
            returnToGachaScreen();
        }).bounds(this.width / 2 - 140, btnY, 135, 22).build();

        this.closeBtn = Button.builder(Component.literal("Confirm"), b -> returnToGachaScreen())
                .bounds(this.width / 2 + 5, btnY, 135, 22).build();

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

            // Trigger impact explosion sound right before transition
            if (phaseTicks >= cutsceneDuration - 8 && !impactSoundPlayed) {
                playImpactSound();
            }

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
        return 52; // 2.6 seconds procedural meteor
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (skipBtn != null && skipBtn.isMouseOver(mouseX, mouseY) && skipBtn.visible) {
                return skipBtn.mouseClicked(mouseX, mouseY, button);
            }

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
    // PHASE 1: METEOR CUTSCENE (PROCEDURAL AAA CELESTIAL METEOR)
    // =========================================================================

    private void renderMeteorCutscene(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Deep Cosmic Nebula Canvas
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF050510);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF0B0720, 0xFF03030A);

        int highestStars = result.highestStars();
        int coreColor = highestStars >= 5 ? 0xFFFFD700 : (highestStars == 4 ? 0xFFC77DFF : 0xFF4CC9F0);
        int auraColor = highestStars >= 5 ? 0xFFFF9E00 : (highestStars == 4 ? 0xFF7209B7 : 0xFF0077B6);

        // Dynamic Nebula Dust Glows
        guiGraphics.fillGradient(0, 0, this.width, this.height / 2,
                (highestStars >= 5 ? 0x223D2005 : (highestStars == 4 ? 0x22260B3D : 0x2205243D)), 0x00000000);

        // 2. Twinkling Starfield with Sinusoidal Pulses
        Random rand = new Random(888L);
        for (int i = 0; i < 54; i++) {
            int sx = rand.nextInt(this.width);
            int sy = rand.nextInt(this.height);
            int starAlpha = 110 + (int) (Math.sin((ticksElapsed * 0.14) + i * 1.7) * 90);
            int starColor = (Math.max(30, Math.min(255, starAlpha)) << 24) | 0x00E4EBFF;
            guiGraphics.fill(sx, sy, sx + 2, sy + 2, starColor);
        }

        // 3. Hyperspace Speed Streaks (Warp speed across deep cosmos)
        for (int i = 0; i < 14; i++) {
            float streakSpeed = 26.0F + (i * 3.5F);
            float streakX = ((ticksElapsed * streakSpeed + i * 85) % (this.width + 300)) - 100;
            float streakY = ((ticksElapsed * (streakSpeed * 0.65F) + i * 65) % (this.height + 200)) - 50;
            int len = 35 + (i * 7);
            int alpha = 40 + (i * 6);
            int col = (alpha << 24) | (coreColor & 0x00FFFFFF);
            guiGraphics.fill((int) streakX, (int) streakY, (int) (streakX + len), (int) (streakY + 1), col);
        }

        // Check if custom animated texture exists
        boolean customPlayed = com.holysweet.linggacha.client.animation.AnimationManager.renderPullAnimation(
                guiGraphics, highestStars, 0, 0, this.width, this.height, phaseTicks * 50L
        );

        if (!customPlayed) {
            float progress = Math.min(1.0F, phaseTicks / 48.0F);

            // Hypersonic Meteor Trajectory with dramatic easing
            float startX = -80.0F;
            float startY = -60.0F;
            float targetX = this.width * 0.65F;
            float targetY = this.height * 0.58F;

            // Ease-in acceleration curve
            float ease = progress * progress * (2.2F - 1.2F * progress);
            float meteorX = startX + (targetX - startX) * ease;
            float meteorY = startY + (targetY - startY) * ease;

            float dirX = targetX - startX;
            float dirY = targetY - startY;
            float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            float ndx = dirX / (dirLen > 0 ? dirLen : 1.0F);
            float ndy = dirY / (dirLen > 0 ? dirLen : 1.0F);

            // 4. Volumetric Multi-Strand Tail
            int trailSteps = 30;
            for (int s = trailSteps; s >= 1; s--) {
                float trailDist = s * 9.5F;
                float tx = meteorX - ndx * trailDist;
                float ty = meteorY - ndy * trailDist;
                float frac = 1.0F - ((float) s / trailSteps);

                // Outer aura strand
                int auraAlpha = (int) (frac * 160);
                int radAura = (int) (18 * frac + 2);
                int auraC = (auraAlpha << 24) | (auraColor & 0x00FFFFFF);
                guiGraphics.fill((int) tx - radAura, (int) ty - radAura, (int) tx + radAura, (int) ty + radAura, auraC);

                // Inner blazing core strand
                int coreAlpha = (int) (frac * 240);
                int radCore = (int) (9 * frac + 1);
                int coreC = (coreAlpha << 24) | (coreColor & 0x00FFFFFF);
                guiGraphics.fill((int) tx - radCore, (int) ty - radCore, (int) tx + radCore, (int) ty + radCore, coreC);

                // Sparkling Embers sprayed in the wake
                if (s % 2 == 0) {
                    float spray = (float) Math.sin(s * 1.8F + phaseTicks * 0.6F) * (14.0F * frac);
                    float ex = tx - ndy * spray;
                    float ey = ty + ndx * spray;
                    int emberAlpha = (int) (frac * 220);
                    int emberC = (emberAlpha << 24) | (coreColor & 0x00FFFFFF);
                    guiGraphics.fill((int) ex - 1, (int) ey - 1, (int) ex + 2, (int) ey + 2, emberC);
                }
            }

            // 5. Blazing Radiant Meteor Head
            int headGlowRad = 34;
            guiGraphics.fill((int) meteorX - headGlowRad, (int) meteorY - headGlowRad,
                    (int) meteorX + headGlowRad, (int) meteorY + headGlowRad, (0x77 << 24) | (auraColor & 0x00FFFFFF));

            int headMidRad = 20;
            guiGraphics.fill((int) meteorX - headMidRad, (int) meteorY - headMidRad,
                    (int) meteorX + headMidRad, (int) meteorY + headMidRad, (0xDD << 24) | (coreColor & 0x00FFFFFF));

            int headCoreRad = 9;
            guiGraphics.fill((int) meteorX - headCoreRad, (int) meteorY - headCoreRad,
                    (int) meteorX + headCoreRad, (int) meteorY + headCoreRad, 0xFFFFFFFF);

            // Diamond Star Glint on Core
            drawStarFlare(guiGraphics, (int) meteorX, (int) meteorY, 32, 0xFFFFFFFF);
            drawStarFlare(guiGraphics, (int) meteorX, (int) meteorY, 18, coreColor);

            // 6. Expanding Resonance Shockwaves
            if (progress > 0.52F) {
                float impactP = (progress - 0.52F) / 0.48F;
                int ringRad = (int) (impactP * 110);
                int ringAlpha = (int) ((1.0F - impactP) * 220);
                int ringCol = (ringAlpha << 24) | (coreColor & 0x00FFFFFF);
                drawShockwaveRings(guiGraphics, (int) targetX, (int) targetY, ringRad, ringCol, 2);
                drawShockwaveRings(guiGraphics, (int) targetX, (int) targetY, (int) (ringRad * 0.6F), ringCol, 1);
            }

            // 7. Celestial Impact Burst (Blinding Flash + Radial Rays)
            if (progress >= 0.84F) {
                float flashP = (progress - 0.84F) / 0.16F;
                int flashAlpha = (int) ((1.0F - flashP) * 230);
                guiGraphics.fill(0, 0, this.width, this.height, (flashAlpha << 24) | (coreColor & 0x00FFFFFF));

                // Radial impact light beams
                drawSunburstRays(guiGraphics, (int) targetX, (int) targetY, 16, 220.0F * flashP, ticksElapsed * 2.0F, coreColor);
            }
        }

        // Subtitle & Status Prompts
        String resonanceText = highestStars >= 5 ? "★ 5-STAR CELESTIAL RESONANCE DETECTED ★" :
                (highestStars == 4 ? "★ 4-STAR FEATURED RESONANCE DETECTED ★" : "CONVENING RESONANCE FREQUENCIES...");

        int badgeW = this.font.width(resonanceText) + 24;
        int badgeX = this.width / 2 - badgeW / 2;
        int badgeY = this.height - 56;

        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 18, 0xCC0D0A1C);
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, coreColor);
        guiGraphics.drawString(this.font, resonanceText, this.width / 2 - this.font.width(resonanceText) / 2, badgeY + 5, coreColor, true);

        String clickPrompt = "Click screen to reveal cards  •  [Skip >>] for all";
        guiGraphics.drawString(this.font, clickPrompt,
                this.width / 2 - this.font.width(clickPrompt) / 2, this.height - 32, 0xFFA0A0C0, true);
    }

    // =========================================================================
    // PHASE 2: SEQUENTIAL CARD REVEAL (3D SUNBURST & FANFARE)
    // =========================================================================

    private void renderSequentialCard(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF060610);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF0D0A1C, 0xFF04040A);

        if (result.prizes().isEmpty()) {
            skipToSummary();
            return;
        }

        ConveneResultPayload.PrizeData prize = result.prizes().get(currentRevealIndex);
        int stars = prize.stars();
        int borderColor = getRarityColor(stars);
        int bgColor = getRarityBg(stars);

        int cx = this.width / 2;
        int cy = this.height / 2 - 10;

        float animScale = Math.min(1.0F, cardRevealTick / 5.0F);

        // Flash burst on first 4 ticks of 5-star or 4-star
        if (stars >= 5 && cardRevealTick < 5) {
            int flashAlpha = (int) ((1.0F - cardRevealTick / 5.0F) * 190);
            guiGraphics.fill(0, 0, this.width, this.height, (flashAlpha << 24) | 0x00FFD700);
        } else if (stars == 4 && cardRevealTick < 4) {
            int flashAlpha = (int) ((1.0F - cardRevealTick / 4.0F) * 140);
            guiGraphics.fill(0, 0, this.width, this.height, (flashAlpha << 24) | 0x00C77DFF);
        }

        // 1. Rotating Volumetric Sunburst Rays Behind Card
        if (stars >= 5) {
            drawSunburstRays(guiGraphics, cx, cy, 16, 190.0F, ticksElapsed * 1.5F, 0xFFFFD700);
        } else if (stars == 4) {
            drawSunburstRays(guiGraphics, cx, cy, 12, 160.0F, ticksElapsed * 1.0F, 0xFFC77DFF);
        }

        int cardW = 210;
        int cardH = 275;
        int cardX = cx - cardW / 2;
        int cardY = cy - cardH / 2;

        // 2. Animated Pulsing Aura Halo
        float pulse = (float) Math.sin(ticksElapsed * 0.16F);
        int haloSize = stars >= 5 ? (int) (14 + 6 * pulse) : (stars == 4 ? 8 : 4);
        int haloAlpha = stars >= 5 ? (int) (60 + 35 * pulse) : 40;
        guiGraphics.fill(cardX - haloSize, cardY - haloSize, cardX + cardW + haloSize, cardY + cardH + haloSize,
                (haloAlpha << 24) | (borderColor & 0x00FFFFFF));

        // 3. Card Outer Border & Inner Glass Box
        guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, borderColor);
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + cardW - 2, cardY + cardH - 2, bgColor);

        // Ambient Floating Particles inside Card
        for (int p = 0; p < 12; p++) {
            float py = (cardY + cardH - 10) - ((ticksElapsed * 1.8F + p * 25.0F) % (cardH - 30));
            float px = cardX + 16 + ((p * 29) % (cardW - 32));
            int pAlpha = (int) (Math.sin((py - cardY) / (float) cardH * Math.PI) * 180);
            if (pAlpha > 20) {
                guiGraphics.fill((int) px, (int) py, (int) px + 2, (int) py + 2,
                        (pAlpha << 24) | (borderColor & 0x00FFFFFF));
            }
        }

        // 4. Ribbon Banner Header
        if (stars >= 5) {
            String bannerTag = "✦ 5★ LEGENDARY // CELESTIAL RESONANCE ✦";
            guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 20, 0xFFD4AF37);
            guiGraphics.drawString(this.font, bannerTag, cx - this.font.width(bannerTag) / 2, cardY + 6, 0xFF160E00, false);
            // Corner Ornaments
            guiGraphics.drawString(this.font, "◆", cardX + 4, cardY + 23, 0xFFFFD700, true);
            guiGraphics.drawString(this.font, "◆", cardX + cardW - 10, cardY + 23, 0xFFFFD700, true);
        } else if (stars == 4) {
            String bannerTag = "✦ 4★ FEATURED // RESONANCE ✦";
            guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + 20, 0xFF8A2BE2);
            guiGraphics.drawString(this.font, bannerTag, cx - this.font.width(bannerTag) / 2, cardY + 6, 0xFFFFFFFF, false);
        }

        // 5. 3D Spinning Item Showcase
        ItemStack stack = getItemStack(prize);
        float time = (System.currentTimeMillis() % 3600000L) / 1000.0F;
        float rot = (time * 42.0F) % 360.0F;
        float bob = (float) Math.sin(time * 2.8F) * 4.5F;

        // Circular glow dais underneath 3D model
        drawShockwaveRings(guiGraphics, cx, cy - 20, 36, (0x44 << 24) | (borderColor & 0x00FFFFFF), 2);

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

        // 6. Stars Rating Row
        String starStr = getStarsSymbols(stars);
        int starY = cardY + cardH - 85;
        guiGraphics.drawString(this.font, starStr, cx - this.font.width(starStr) / 2, starY, borderColor, true);

        // 7. Item Display Name
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
        guiGraphics.drawString(this.font, clickNext, cx - this.font.width(clickNext) / 2, this.height - 28, 0xFFA0A0C0, true);
    }

    // =========================================================================
    // PHASE 3: RESULTS SHOWCASE GRID (10-CARD BREATHING AURA & HOVER TOOLTIPS)
    // =========================================================================

    private void renderSummaryGrid(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF080814);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF0F0B24, 0xFF05050C);

        // Header Title
        String title = "✦ CONVENE RESULTS ✦";
        int titleX = this.width / 2 - this.font.width(title) / 2;
        guiGraphics.drawString(this.font, title, titleX, 13, 0xFFFFD700, true);
        guiGraphics.fill(titleX - 16, 24, titleX + this.font.width(title) + 16, 25, 0x88FFD700);

        // Corals earned pill
        String coralText = "+ " + result.corals() + " Afterglow Corals Total";
        int coralW = this.font.width(coralText) + 14;
        int coralX = this.width / 2 - coralW / 2;
        guiGraphics.fill(coralX, 29, coralX + coralW, 43, 0xDD2A163B);
        guiGraphics.fill(coralX + 1, 30, coralX + coralW - 1, 42, 0xEE1E0F2B);
        guiGraphics.drawString(this.font, coralText, coralX + 7, 32, 0xFFC77DFF, true);

        List<ConveneResultPayload.PrizeData> prizes = result.prizes();
        if (prizes.size() == 1) {
            renderSingleSummaryCard(guiGraphics, prizes.get(0), mouseX, mouseY);
        } else {
            renderTenSummaryGrid(guiGraphics, prizes, mouseX, mouseY);
        }

        // Bottom hint
        String hint = "§8• §7Hover over card to inspect details & stats";
        guiGraphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 48, 0xFFA0A0C0, true);
    }

    private void renderSingleSummaryCard(GuiGraphics guiGraphics, ConveneResultPayload.PrizeData prize, int mouseX, int mouseY) {
        int cardW = 160;
        int cardH = 205;
        int cardX = this.width / 2 - cardW / 2;
        int cardY = this.height / 2 - cardH / 2 - 10;

        int borderColor = getRarityColor(prize.stars());
        int bgColor = getRarityBg(prize.stars());

        // Pulsing Aura Halo
        float pulse = (float) Math.sin(ticksElapsed * 0.16F);
        int haloSize = prize.stars() >= 5 ? (int) (10 + 4 * pulse) : (prize.stars() == 4 ? 6 : 3);
        int haloAlpha = prize.stars() >= 5 ? (int) (70 + 35 * pulse) : 40;
        guiGraphics.fill(cardX - haloSize, cardY - haloSize, cardX + cardW + haloSize, cardY + cardH + haloSize,
                (haloAlpha << 24) | (borderColor & 0x00FFFFFF));

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
        guiGraphics.drawString(this.font, stars, cardX + cardW / 2 - this.font.width(stars) / 2, cardY + 118, borderColor, true);

        // Name
        String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
        guiGraphics.drawString(this.font, name, cardX + cardW / 2 - this.font.width(name) / 2, cardY + 138, 0xFFFFFFFF, true);

        if (prize.count() > 1) {
            String countStr = "x" + prize.count();
            guiGraphics.drawString(this.font, countStr, cardX + cardW / 2 - this.font.width(countStr) / 2, cardY + 155, 0xFFFFD700, true);
        }

        // Hover Tooltip Check
        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
            hoveredTooltipStack = stack;
        }
    }

    private void renderTenSummaryGrid(GuiGraphics guiGraphics, List<ConveneResultPayload.PrizeData> prizes, int mouseX, int mouseY) {
        int cardW = 82;
        int cardH = 114;
        int gapX = 12;
        int gapY = 12;

        int totalW = 5 * cardW + 4 * gapX;
        int totalH = 2 * cardH + gapY;
        int startX = this.width / 2 - totalW / 2;
        int startY = this.height / 2 - totalH / 2 - 8;

        for (int i = 0; i < prizes.size() && i < 10; i++) {
            ConveneResultPayload.PrizeData prize = prizes.get(i);
            int col = i % 5;
            int row = i / 5;

            int x = startX + col * (cardW + gapX);
            int y = startY + row * (cardH + gapY);

            int borderColor = getRarityColor(prize.stars());
            int bgColor = getRarityBg(prize.stars());

            boolean hovered = (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH);

            // Glowing Breathing Aura for 5★ and 4★ cards
            if (prize.stars() >= 5) {
                float pulse = (float) Math.sin((ticksElapsed + i * 4) * 0.18F);
                int auraAlpha = (int) (130 + 70 * pulse);
                guiGraphics.fill(x - 4, y - 4, x + cardW + 4, y + cardH + 4, (auraAlpha << 24) | 0x00FFD700);

                // Floating sparkle motes above 5★ card
                int sparkleY = y + cardH - (int) ((ticksElapsed * 1.5F + i * 16) % (cardH + 10));
                int sparkleX = x + 12 + ((i * 31) % (cardW - 24));
                guiGraphics.fill(sparkleX, sparkleY, sparkleX + 2, sparkleY + 2, 0xFFFFD700);
            } else if (prize.stars() == 4) {
                float pulse = (float) Math.sin((ticksElapsed + i * 4) * 0.16F);
                int auraAlpha = (int) (90 + 50 * pulse);
                guiGraphics.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, (auraAlpha << 24) | 0x00C77DFF);
            }

            // Hover Frame Highlight
            if (hovered) {
                guiGraphics.fill(x - 2, y - 2, x + cardW + 2, y + cardH + 2, 0xFFFFFFFF);
            }

            // Main Card Box
            guiGraphics.fill(x, y, x + cardW, y + cardH, borderColor);
            guiGraphics.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, hovered ? 0xEE30264E : bgColor);

            // Corner Diamond Accent for 5★
            if (prize.stars() >= 5) {
                guiGraphics.drawString(this.font, "◆", x + 3, y + 2, 0xFFFFD700, false);
                guiGraphics.drawString(this.font, "◆", x + cardW - 9, y + 2, 0xFFFFD700, false);
            }

            // Item Slot Frame & Icon
            ItemStack stack = getItemStack(prize);
            int slotX = x + cardW / 2 - 12;
            int slotY = y + 16;
            guiGraphics.fill(slotX, slotY, slotX + 24, slotY + 24, borderColor);
            guiGraphics.fill(slotX + 1, slotY + 1, slotX + 23, slotY + 23, 0xCC0D0B18);

            guiGraphics.renderItem(stack, slotX + 4, slotY + 4);
            guiGraphics.renderItemDecorations(this.font, stack, slotX + 4, slotY + 4);

            // Stars
            String stars = getStarsSymbols(prize.stars());
            guiGraphics.drawString(this.font, stars, x + cardW / 2 - this.font.width(stars) / 2, y + 54, borderColor, true);

            // Item Display Name
            String name = (prize.name() != null && !prize.name().isEmpty()) ? prize.name() : stack.getHoverName().getString();
            if (this.font.width(name) > cardW - 8) {
                name = name.substring(0, Math.min(name.length(), 6)) + "..";
            }
            int nameColor = prize.stars() >= 5 ? 0xFFFFD166 : (prize.stars() == 4 ? 0xFFE0AAFF : 0xFFFFFFFF);
            guiGraphics.drawString(this.font, name, x + cardW / 2 - this.font.width(name) / 2, y + 72, nameColor, true);

            // Count badge
            if (prize.count() > 1) {
                String countStr = "x" + prize.count();
                guiGraphics.drawString(this.font, countStr, x + cardW - this.font.width(countStr) - 5, y + 94, 0xFFFFD700, true);
            }

            if (hovered) {
                hoveredTooltipStack = stack;
            }
        }
    }

    // =========================================================================
    // AUDIO & CELESTIAL VFX HELPERS
    // =========================================================================

    private void playCutsceneSound() {
        if (Minecraft.getInstance().player != null) {
            var sm = Minecraft.getInstance().getSoundManager();
            sm.play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.85F));
            if (result.highestStars() >= 5) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.2F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F));
            } else if (result.highestStars() == 4) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.3F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F));
            } else {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_STEP, 1.0F));
            }
        }
    }

    private void playImpactSound() {
        if (!impactSoundPlayed && Minecraft.getInstance().player != null) {
            impactSoundPlayed = true;
            var sm = Minecraft.getInstance().getSoundManager();
            sm.play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F));
            if (result.highestStars() >= 5) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.2F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F));
            } else if (result.highestStars() == 4) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.1F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F));
            } else {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_STEP, 1.1F));
            }
        }
    }

    private void playCardRevealSound(int stars) {
        if (Minecraft.getInstance().player != null) {
            var sm = Minecraft.getInstance().getSoundManager();
            if (stars >= 5) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1.15F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2F));
            } else if (stars == 4) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.15F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0F));
            } else {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
            }
        }
    }

    private void playSummarySound() {
        if (Minecraft.getInstance().player != null) {
            var sm = Minecraft.getInstance().getSoundManager();
            if (result.highestStars() >= 5) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F));
                sm.play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.2F));
            } else if (result.highestStars() == 4) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
            } else {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
    }

    private void drawSunburstRays(GuiGraphics guiGraphics, int cx, int cy, int numRays, float rayLength, float rotation, int color) {
        for (int i = 0; i < numRays; i++) {
            double angle = Math.toRadians(rotation + i * (360.0F / numRays));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int steps = 18;
            for (int s = 1; s <= steps; s++) {
                float dist = (s / (float) steps) * rayLength;
                int px = (int) (cx + cos * dist);
                int py = (int) (cy + sin * dist);
                int alpha = (int) ((1.0F - (s / (float) steps)) * 80);
                int c = (alpha << 24) | (color & 0x00FFFFFF);
                int size = Math.max(1, (int) (s * 0.75F));
                guiGraphics.fill(px - size, py - size, px + size, py + size, c);
            }
        }
    }

    private void drawStarFlare(GuiGraphics guiGraphics, int cx, int cy, int length, int color) {
        for (int d = -length; d <= length; d++) {
            float fade = 1.0F - Math.abs(d) / (float) length;
            int a = (int) (fade * 255);
            int c = (a << 24) | (color & 0x00FFFFFF);
            int thick = Math.max(1, (int) (fade * 3));
            guiGraphics.fill(cx + d, cy - thick, cx + d + 1, cy + thick, c);
            guiGraphics.fill(cx - thick, cy + d, cx + thick, cy + d + 1, c);
        }
    }

    private void drawShockwaveRings(GuiGraphics guiGraphics, int cx, int cy, int radius, int color, int thickness) {
        int step = Math.max(4, 360 / Math.max(12, radius * 2));
        for (int a = 0; a < 360; a += step) {
            double rad = Math.toRadians(a);
            int px = cx + (int) (Math.cos(rad) * radius);
            int py = cy + (int) (Math.sin(rad) * radius);
            guiGraphics.fill(px - thickness, py - thickness, px + thickness, py + thickness, color);
        }
    }

    private int getRarityColor(int stars) {
        if (stars >= 5) return 0xFFFFB703;
        if (stars == 4) return 0xFFC77DFF;
        return 0xFF4CC9F0;
    }

    private int getRarityBg(int stars) {
        if (stars >= 5) return 0xFF2A1C08;
        if (stars == 4) return 0xFF1E102E;
        return 0xFF0D1629;
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
