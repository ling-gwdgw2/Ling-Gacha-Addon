package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.network.AdminSyncItemPoolPayload;
import com.holysweet.linggacha.network.PullConvenePayload;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import com.holysweet.linggacha.network.SyncMailboxPayload;
import com.holysweet.linggacha.network.SyncPullHistoryPayload;
import com.holysweet.questshop.client.ClientCoins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class GachaScreen extends Screen {

    private static final ResourceLocation GACHA_ICON = ResourceLocation.fromNamespaceAndPath("ling_gacha",
            "textures/gui/icon.png");

    private int selectedBannerIndex = 0;
    private final int sidebarWidth = 145;
    private final int headerHeight = 32;
    private final int bottomBarHeight = 42;

    // Normal User Controls
    private Button pull1Btn;
    private Button pull10Btn;
    private Button detailsBtn;
    private Button historyBtn;
    private Button mailboxBtn;
    private Button closeBtn;

    // Admin / OP Controls
    private boolean editMode = false;
    private Button editModeToggleBtn;
    private Button bannerSettingsBtn;
    private Button poolManagerBtn;
    private Button newBannerBtn;

    // Active Modals
    private GachaDetailsModal activeDetailsModal = null;
    private GachaHistoryModal activeHistoryModal = null;
    private GachaMailboxModal activeMailboxModal = null;
    private BannerSettingsModal activeBannerSettingsModal = null;
    private ItemPoolEditModal activeItemPoolModal = null;
    private ItemAddEditModal activeItemAddEditModal = null;

    public GachaScreen() {
        super(Component.literal("Convene / Gacha"));
        this.minecraft = Minecraft.getInstance();
    }

    public int getSidebarWidth() {
        return sidebarWidth;
    }

    public int getHeaderHeight() {
        return headerHeight;
    }

    public int getBottomBarHeight() {
        return bottomBarHeight;
    }

    @Override
    protected void init() {
        super.init();

        this.selectedBannerIndex = ClientGachaData.getLastSelectedBannerIndex();
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        if (!banners.isEmpty()) {
            this.selectedBannerIndex = Math.max(0, Math.min(banners.size() - 1, selectedBannerIndex));
        }

        int actionY = this.height - bottomBarHeight + 10;

        // Bottom Action Controls
        this.detailsBtn = Button.builder(Component.literal("Details"), b -> openDetails())
                .bounds(14, actionY, 68, 22).build();
        this.addRenderableWidget(detailsBtn);

        this.historyBtn = Button.builder(Component.literal("History"), b -> openHistory())
                .bounds(86, actionY, 68, 22).build();
        this.addRenderableWidget(historyBtn);

        this.mailboxBtn = Button.builder(Component.literal(getMailboxButtonText()), b -> openMailbox())
                .bounds(158, actionY, 95, 22).build();
        this.addRenderableWidget(mailboxBtn);

        this.pull1Btn = Button.builder(Component.literal("Convene 1x"), b -> pull(1))
                .bounds(this.width - 290, actionY, 135, 22).build();
        this.addRenderableWidget(pull1Btn);

        this.pull10Btn = Button.builder(Component.literal("Convene 10x"), b -> pull(10))
                .bounds(this.width - 145, actionY, 135, 22).build();
        this.addRenderableWidget(pull10Btn);

        // Top Right Close [X] Button
        this.closeBtn = Button.builder(Component.literal("X"), b -> this.onClose())
                .bounds(this.width - 28, 6, 20, 20).build();
        this.addRenderableWidget(closeBtn);

        // Admin Controls (OP, Creative Mode, or Singleplayer Host)
        boolean canAdmin = false;
        if (Minecraft.getInstance().player != null) {
            canAdmin = Minecraft.getInstance().player.hasPermissions(2) ||
                    Minecraft.getInstance().player.isCreative() ||
                    Minecraft.getInstance().hasSingleplayerServer();
        }

        if (canAdmin) {
            this.editModeToggleBtn = Button
                    .builder(Component.literal(editMode ? "§c[Exit Edit]" : "§e[Edit Mode]"), b -> toggleEditMode())
                    .bounds(this.width - 390, 6, 92, 20).build();
            this.addRenderableWidget(editModeToggleBtn);

            int adminBtnY = headerHeight + 8;
            int adminBtnX = sidebarWidth + 16;

            this.newBannerBtn = Button.builder(Component.literal("§a+ New Banner"), b -> createNewBanner())
                    .bounds(adminBtnX, adminBtnY, 95, 18).build();
            this.newBannerBtn.visible = editMode;
            this.addRenderableWidget(newBannerBtn);

            this.bannerSettingsBtn = Button
                    .builder(Component.literal("§eSettings"), b -> openBannerSettingsModal(getCurrentBanner(), false))
                    .bounds(adminBtnX + 105, adminBtnY, 85, 18).build();
            this.bannerSettingsBtn.visible = editMode;
            this.addRenderableWidget(bannerSettingsBtn);

            this.poolManagerBtn = Button
                    .builder(Component.literal("§bPool (" + getCurrentBanner().totalItems() + ")"),
                            b -> openItemPoolModal(getCurrentBanner().id(), getCurrentBanner().title()))
                    .bounds(adminBtnX + 200, adminBtnY, 105, 18).build();
            this.poolManagerBtn.visible = editMode;
            this.addRenderableWidget(poolManagerBtn);
        }

        updateButtons();
    }

    private void toggleEditMode() {
        this.editMode = !editMode;
        if (editModeToggleBtn != null) {
            editModeToggleBtn.setMessage(Component.literal(editMode ? "§c[Exit Edit]" : "§e[Edit Mode]"));
        }
        if (bannerSettingsBtn != null)
            bannerSettingsBtn.visible = editMode;
        if (poolManagerBtn != null)
            poolManagerBtn.visible = editMode;
        if (newBannerBtn != null)
            newBannerBtn.visible = editMode;
    }

    public void refreshData() {
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        if (!banners.isEmpty()) {
            this.selectedBannerIndex = Math.max(0, Math.min(banners.size() - 1, selectedBannerIndex));
        }
        updateButtons();
    }

    public void onItemPoolSynced(AdminSyncItemPoolPayload payload) {
        if (activeItemPoolModal != null) {
            activeItemPoolModal.updateItems(payload.items());
        }
    }

    public void onHistorySynced(SyncPullHistoryPayload payload) {
        if (activeHistoryModal != null) {
            activeHistoryModal.updateData(payload);
        }
    }

    public void onMailboxSynced(SyncMailboxPayload payload) {
        if (activeMailboxModal != null) {
            activeMailboxModal.updateData(payload);
        }
        updateButtons();
    }

    private String getMailboxButtonText() {
        int count = ClientGachaData.getMailboxCount();
        return count > 0 ? "§aMailbox (" + count + ")" : "Mailbox";
    }

    private void openDetails() {
        closeModal();
        this.activeDetailsModal = new GachaDetailsModal(this, getCurrentBanner());
        this.activeDetailsModal.init(this.width, this.height);
    }

    private void openHistory() {
        closeModal();
        this.activeHistoryModal = new GachaHistoryModal(this, getCurrentBanner().id());
        this.activeHistoryModal.init(this.width, this.height);
    }

    private void openMailbox() {
        closeModal();
        this.activeMailboxModal = new GachaMailboxModal(this);
        this.activeMailboxModal.init(this.width, this.height);
    }

    public void openBannerSettingsModal(SyncBannerDataPayload.ClientBannerInfo banner, boolean isNew) {
        closeModal();
        this.activeBannerSettingsModal = new BannerSettingsModal(this, banner, isNew);
        this.activeBannerSettingsModal.init(this.width, this.height);
    }

    public void openItemPoolModal(String bannerId, String bannerTitle) {
        closeModal();
        this.activeItemPoolModal = new ItemPoolEditModal(this, bannerId, bannerTitle);
        this.activeItemPoolModal.init(this.width, this.height);
    }

    public void openItemAddModal(String bannerId, ItemStack heldStack) {
        this.activeItemAddEditModal = new ItemAddEditModal(this, bannerId, heldStack);
        this.activeItemAddEditModal.init(this.width, this.height);
    }

    public void openItemEditModal(String bannerId, AdminSyncItemPoolPayload.ItemEntryData itemData) {
        this.activeItemAddEditModal = new ItemAddEditModal(this, bannerId, itemData);
        this.activeItemAddEditModal.init(this.width, this.height);
    }

    public void closeItemAddEditModal() {
        this.activeItemAddEditModal = null;
    }

    public void closeModal() {
        this.activeDetailsModal = null;
        this.activeHistoryModal = null;
        this.activeMailboxModal = null;
        this.activeBannerSettingsModal = null;
        this.activeItemPoolModal = null;
        this.activeItemAddEditModal = null;
    }

    public void closeDetails() {
        this.activeDetailsModal = null;
    }

    private void createNewBanner() {
        String newId = "custom_banner_" + (ClientGachaData.getBanners().size() + 1);
        SyncBannerDataPayload.ClientBannerInfo dummy = new SyncBannerDataPayload.ClientBannerInfo(
                newId, "New Banner", "Custom Pool", "FEATURED_RESONATOR", 160, 0, "minecraft:diamond_sword", null, null,
                null, 0, 0);
        openBannerSettingsModal(dummy, true);
    }

    private SyncBannerDataPayload.ClientBannerInfo getCurrentBanner() {
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        if (banners.isEmpty()) {
            return new SyncBannerDataPayload.ClientBannerInfo("default", "Convene", "Convene Pool", "STANDARD", 160, 0,
                    "minecraft:netherite_sword", null, null, null, 0, 0);
        }
        int idx = Math.max(0, Math.min(banners.size() - 1, selectedBannerIndex));
        return banners.get(idx);
    }

    private void pull(int count) {
        SyncBannerDataPayload.ClientBannerInfo banner = getCurrentBanner();
        ClientGachaData.setLastSelectedBannerIndex(selectedBannerIndex);
        PacketDistributor.sendToServer(new PullConvenePayload(banner.id(), count));
    }

    private void updateButtons() {
        SyncBannerDataPayload.ClientBannerInfo banner = getCurrentBanner();
        int cost1 = banner.cost();
        int cost10 = banner.discount() > 0 ? (cost1 * 10 * (100 - banner.discount())) / 100 : cost1 * 10;

        if (pull1Btn != null) {
            pull1Btn.setMessage(Component.literal("Convene 1x (" + cost1 + " G)"));
        }
        if (pull10Btn != null) {
            String discountStr = banner.discount() > 0 ? " [" + banner.discount() + "% OFF]" : "";
            pull10Btn.setMessage(Component.literal("Convene 10x (" + cost10 + " G)" + discountStr));
        }
        if (mailboxBtn != null) {
            mailboxBtn.setMessage(Component.literal(getMailboxButtonText()));
        }
        if (poolManagerBtn != null) {
            poolManagerBtn.setMessage(Component.literal("§bPool (" + banner.totalItems() + ")"));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeItemAddEditModal != null) {
            return activeItemAddEditModal.mouseClicked(mouseX, mouseY, button);
        }
        if (activeItemPoolModal != null) {
            return activeItemPoolModal.mouseClicked(mouseX, mouseY, button);
        }
        if (activeBannerSettingsModal != null) {
            return activeBannerSettingsModal.mouseClicked(mouseX, mouseY, button);
        }
        if (activeDetailsModal != null) {
            return activeDetailsModal.mouseClicked(mouseX, mouseY, button);
        }
        if (activeHistoryModal != null) {
            return activeHistoryModal.mouseClicked(mouseX, mouseY, button);
        }
        if (activeMailboxModal != null) {
            return activeMailboxModal.mouseClicked(mouseX, mouseY, button);
        }

        // Check Banner Sidebar tabs
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        int tabX = 6;
        int tabY = headerHeight + 8;
        int tabW = sidebarWidth - 12;
        int tabH = 28;

        for (int i = 0; i < banners.size(); i++) {
            int y = tabY + i * (tabH + 4);
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH) {
                this.selectedBannerIndex = i;
                ClientGachaData.setLastSelectedBannerIndex(i);
                updateButtons();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeItemPoolModal != null) {
            return activeItemPoolModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (activeHistoryModal != null) {
            return activeHistoryModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (activeMailboxModal != null) {
            return activeMailboxModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeItemAddEditModal != null) {
            return activeItemAddEditModal.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeItemPoolModal != null) {
            return activeItemPoolModal.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeBannerSettingsModal != null) {
            return activeBannerSettingsModal.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeDetailsModal != null) {
            return activeDetailsModal.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeHistoryModal != null) {
            return activeHistoryModal.keyPressed(keyCode, scanCode, modifiers);
        }
        if (activeMailboxModal != null) {
            return activeMailboxModal.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeItemAddEditModal != null) {
            return activeItemAddEditModal.charTyped(codePoint, modifiers);
        }
        if (activeBannerSettingsModal != null) {
            return activeBannerSettingsModal.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Prevent Minecraft 1.21.1 world blur
    }

    @Override
    public void renderMenuBackground(GuiGraphics guiGraphics) {
        // Prevent background blur
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. FULLSCREEN CANVAS BASE BACKGROUND
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF080812);

        // 2. MAIN BANNER STAGE AREA
        int stageX = sidebarWidth;
        int stageY = headerHeight;
        int stageW = this.width - sidebarWidth;
        int stageH = this.height - headerHeight - bottomBarHeight;

        // Stage Base Color
        guiGraphics.fill(stageX, stageY, stageX + stageW, stageY + stageH, 0xFF101020);

        SyncBannerDataPayload.ClientBannerInfo current = getCurrentBanner();

        // Render Custom Background Splash Art / Animated Texture (.gif / .afma / .png)
        boolean bgRendered = false;
        if (current.backgroundImage() != null && !current.backgroundImage().trim().isEmpty()) {
            bgRendered = com.holysweet.linggacha.client.animation.AnimationManager.renderBannerBackground(
                    guiGraphics, current.backgroundImage().trim(), stageX, stageY, stageW, stageH);
        }
        if (!bgRendered) {
            // Default elegant deep-space gradient
            guiGraphics.fillGradient(stageX, stageY, stageX + stageW, stageY + stageH, 0xFF151228, 0xFF0B0B16);
        }

        // Banner Stage Typography (Left Side of Stage)
        int textOffsetAdmin = editMode ? 26 : 0;
        int bannerTitleY = stageY + 20 + textOffsetAdmin;
        guiGraphics.drawString(this.font, current.title().toUpperCase(), stageX + 24, bannerTitleY, 0xFFFFD166, true);
        guiGraphics.drawString(this.font, current.subtitle(), stageX + 24, bannerTitleY + 14, 0xFFB8B8D4, true);

        // --- 3D ROTATING & FLOATING PREVIEW ITEM SHOWCASE (Right-Center of Stage) ---
        ItemStack previewStack = getPreviewStack(current.previewItem(), current.featuredSnbt());
        int itemCenterX = stageX + stageW - Math.max(110, stageW / 3);
        int itemCenterY = stageY + stageH / 2 - 10;
        int boxW = 80;
        int boxH = 80;

        // Glowing Pedestal Holographic Rings
        int ringRadiusX = 40;
        int ringRadiusY = 6;
        int pedestalY = itemCenterY + 36;
        guiGraphics.fill(itemCenterX - ringRadiusX, pedestalY, itemCenterX + ringRadiusX, pedestalY + 2, 0xFFFFB703);
        guiGraphics.fill(itemCenterX - ringRadiusX + 6, pedestalY - 1, itemCenterX + ringRadiusX - 6, pedestalY + 3,
                0x88FFD166);
        guiGraphics.fill(itemCenterX - ringRadiusX + 12, pedestalY - 2, itemCenterX + ringRadiusX - 12, pedestalY + 4,
                0x44FFEAA7);

        // Compute Smooth 3D Rotation & Floating Bobbing
        float time = (System.currentTimeMillis() % 3600000L) / 1000.0F;
        float rotationAngle = (time * 45.0F) % 360.0F;
        float bobbing = (float) Math.sin(time * 2.2F) * 3.5F;

        // Render Real 3D Scaled Rotating Item Model
        float modelScale = Math.min(52.0F, stageH * 0.26F);
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(itemCenterX, itemCenterY + bobbing, 150.0F);
        pose.scale(modelScale, -modelScale, modelScale);
        pose.mulPose(Axis.XP.rotationDegrees(16.0F)); // Tilt slightly forward
        pose.mulPose(Axis.YP.rotationDegrees(rotationAngle)); // Smooth spin around Y

        Minecraft.getInstance().getItemRenderer().renderStatic(
                previewStack,
                ItemDisplayContext.FIXED,
                15728880, // Full bright
                OverlayTexture.NO_OVERLAY,
                pose,
                guiGraphics.bufferSource(),
                Minecraft.getInstance().level,
                0);
        guiGraphics.flush();
        pose.popPose();

        // Dynamic Featured Item Name & Badge
        String displayName = (current.featuredItemName() != null && !current.featuredItemName().isEmpty())
                ? current.featuredItemName()
                : previewStack.getHoverName().getString();

        int nameW = this.font.width(displayName);
        int badgeX = itemCenterX - nameW / 2 - 8;
        int badgeY = pedestalY + 10;
        guiGraphics.fill(badgeX, badgeY, badgeX + nameW + 16, badgeY + 24, 0xCC141424);
        guiGraphics.fill(badgeX + 1, badgeY + 1, badgeX + nameW + 15, badgeY + 23, 0xCC1C1C36);
        guiGraphics.drawString(this.font, displayName, itemCenterX - nameW / 2, badgeY + 4, 0xFFFFD700, true);

        String rateUpText = "5-STAR RATE-UP SHOWCASE";
        guiGraphics.drawString(this.font, rateUpText, itemCenterX - this.font.width(rateUpText) / 2, badgeY + 14,
                0xFFFFB703, true);

        // 3. LEFT SIDEBAR (Banner Tabs)
        guiGraphics.fill(0, headerHeight, sidebarWidth, this.height - bottomBarHeight, 0xFA0D0D18);
        guiGraphics.fill(sidebarWidth - 1, headerHeight, sidebarWidth, this.height - bottomBarHeight, 0xFF222238); // Divider
                                                                                                                   // line

        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        int tabX = 6;
        int tabY = headerHeight + 8;
        int tabW = sidebarWidth - 12;
        int tabH = 28;

        for (int i = 0; i < banners.size(); i++) {
            SyncBannerDataPayload.ClientBannerInfo b = banners.get(i);
            int y = tabY + i * (tabH + 4);
            boolean selected = (i == selectedBannerIndex);
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH;

            int bg = selected ? 0xFF3D2C5E : (hovered ? 0xFF24243A : 0xFF161626);
            guiGraphics.fill(tabX, y, tabX + tabW, y + tabH, bg);
            if (selected) {
                guiGraphics.fill(tabX, y, tabX + 3, y + tabH, 0xFFFFB703); // Golden vertical accent
            }

            int textColor = selected ? 0xFFFFD166 : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA);
            String tabTitle = b.title();
            if (this.font.width(tabTitle) > tabW - 14) {
                tabTitle = tabTitle.substring(0, Math.min(tabTitle.length(), 14)) + "..";
            }
            guiGraphics.drawString(this.font, tabTitle, tabX + 8, y + 10, textColor, true);
        }

        // 4. TOP HEADER BAR
        guiGraphics.fill(0, 0, this.width, headerHeight, 0xFA0A0A16);
        guiGraphics.fill(0, headerHeight - 1, this.width, headerHeight, 0xFF222238);

        // Header Title
        guiGraphics.drawString(this.font, "CONVENE", 16, 11, 0xFFEEEEEE, true);

        // Calculate dynamic positions from right to left to prevent overlaps
        int rightEdge = this.width - 34; // before close button [X]

        // Primogems Pill
        String coinsText = "Primogems: " + ClientCoins.get() + " G";
        int coinsTextW = this.font.width(coinsText);
        int coinPillW = coinsTextW + 12;
        int coinPillX = rightEdge - coinPillW;

        guiGraphics.fill(coinPillX, 6, coinPillX + coinPillW, 26, 0xFF3D3000);
        guiGraphics.fill(coinPillX + 1, 7, coinPillX + coinPillW - 1, 25, 0xFF1C1600);
        guiGraphics.drawString(this.font, coinsText, coinPillX + 6, 11, 0xFFFFD700, true);

        // Corals Pill
        String coralsText = "Corals: " + ClientGachaData.getCorals();
        int coralsTextW = this.font.width(coralsText);
        int coralPillW = coralsTextW + 12;
        int coralPillX = coinPillX - coralPillW - 8;

        guiGraphics.fill(coralPillX, 6, coralPillX + coralPillW, 26, 0xFF351A4A);
        guiGraphics.fill(coralPillX + 1, 7, coralPillX + coralPillW - 1, 25, 0xFF1A0A26);
        guiGraphics.drawString(this.font, coralsText, coralPillX + 6, 11, 0xFFC77DFF, true);

        // Reposition Edit Mode Toggle Button safely
        if (editModeToggleBtn != null) {
            editModeToggleBtn.setX(coralPillX - 98);
            editModeToggleBtn.setY(6);
        }

        // 5. BOTTOM ACTION BAR
        int bottomY = this.height - bottomBarHeight;
        guiGraphics.fill(0, bottomY, this.width, this.height, 0xFA0A0A16);
        guiGraphics.fill(0, bottomY, this.width, bottomY + 1, 0xFF222238);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Tooltip when hovering over 3D item showcase
        if (activeDetailsModal == null && activeHistoryModal == null && activeMailboxModal == null
                && activeItemAddEditModal == null && activeItemPoolModal == null && activeBannerSettingsModal == null) {
            if (mouseX >= itemCenterX - boxW / 2 && mouseX <= itemCenterX + boxW / 2 && mouseY >= itemCenterY - boxH / 2
                    && mouseY <= itemCenterY + boxH / 2 + 30) {
                guiGraphics.renderTooltip(this.font, previewStack, mouseX, mouseY);
            }
        }

        // 6. RENDER ACTIVE MODAL OVERLAYS (Full-Screen Centered with Dim)
        if (activeDetailsModal != null) {
            activeDetailsModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        } else if (activeHistoryModal != null) {
            activeHistoryModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        } else if (activeMailboxModal != null) {
            activeMailboxModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        } else if (activeItemAddEditModal != null) {
            activeItemAddEditModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        } else if (activeItemPoolModal != null) {
            activeItemPoolModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        } else if (activeBannerSettingsModal != null) {
            activeBannerSettingsModal.render(guiGraphics, mouseX, mouseY, partialTick, this.width, this.height);
        }
    }

    private ItemStack getPreviewStack(String itemId, String snbt) {
        ItemStack stack = ItemStack.EMPTY;
        if (snbt != null && !snbt.isEmpty()) {
            try {
                var tag = TagParser.parseTag(snbt);
                if (Minecraft.getInstance().level != null) {
                    stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), tag);
                }
            } catch (Exception ignored) {
            }
        }
        if (stack.isEmpty()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(itemId);
                var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (itemOpt.isPresent()) {
                    stack = new ItemStack(itemOpt.get(), 1);
                }
            } catch (Exception ignored) {
            }
        }
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.NETHERITE_SWORD, 1);
        }
        return stack;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
