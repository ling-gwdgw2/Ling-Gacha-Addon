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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GachaScreen extends Screen {

    private static final ResourceLocation GACHA_ICON = ResourceLocation.fromNamespaceAndPath("ling_gacha",
            "textures/gui/icon.png");

    private int selectedBannerIndex = 0;
    private final int sidebarWidth = 145;
    private final int headerHeight = 32;
    private final int bottomBarHeight = 42;

    // Banner Item Pool State
    private int poolScrollOffset = 0;
    private int poolFilter = 0; // 0 = ALL, 5 = 5-Star, 4 = 4-Star, 3 = 3-Star
    private ItemStack hoveredPoolStack = ItemStack.EMPTY;
    private final Map<String, ItemStack> itemStackCache = new HashMap<>();

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

        // Admin Controls (OP Level 2 or Singleplayer Host)
        boolean canAdmin = false;
        if (Minecraft.getInstance().player != null) {
            canAdmin = Minecraft.getInstance().player.hasPermissions(2) ||
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
                null, 0, 0, Collections.emptyList());
        openBannerSettingsModal(dummy, true);
    }

    private SyncBannerDataPayload.ClientBannerInfo getCurrentBanner() {
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        if (banners.isEmpty()) {
            return new SyncBannerDataPayload.ClientBannerInfo("default", "Convene", "Convene Pool", "STANDARD", 160, 0,
                    "minecraft:netherite_sword", null, null, null, 0, 0, Collections.emptyList());
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
        int normal = cost1 * 10;
        int discountAmt = banner.discount() > 0 ? (normal * banner.discount()) / 100 : 0;
        int cost10 = Math.max(1, normal - discountAmt);

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
                this.poolScrollOffset = 0;
                ClientGachaData.setLastSelectedBannerIndex(i);
                updateButtons();
                return true;
            }
        }

        // Check Banner Item Pool filter pills (when no modal is active)
        int stageX = sidebarWidth;
        int stageY = headerHeight;
        int stageW = this.width - sidebarWidth;
        int poolPanelW = Math.max(260, Math.min(360, (int) (stageW * 0.52f)));
        int poolPanelX = stageX + stageW - poolPanelW - 16;
        int poolPanelY = stageY + 12;
        int filterY = poolPanelY + 26 + 6;
        int filterH = 16;

        if (mouseY >= filterY && mouseY <= filterY + filterH) {
            if (mouseX >= poolPanelX + 8 && mouseX <= poolPanelX + 48) {
                this.poolFilter = 0;
                this.poolScrollOffset = 0;
                return true;
            } else if (mouseX >= poolPanelX + 52 && mouseX <= poolPanelX + 88) {
                this.poolFilter = 5;
                this.poolScrollOffset = 0;
                return true;
            } else if (mouseX >= poolPanelX + 92 && mouseX <= poolPanelX + 128) {
                this.poolFilter = 4;
                this.poolScrollOffset = 0;
                return true;
            } else if (mouseX >= poolPanelX + 132 && mouseX <= poolPanelX + 168) {
                this.poolFilter = 3;
                this.poolScrollOffset = 0;
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

        // Scroll Banner Item Pool panel
        int stageX = sidebarWidth;
        int stageY = headerHeight;
        int stageW = this.width - sidebarWidth;
        int stageH = this.height - headerHeight - bottomBarHeight;
        int poolPanelW = Math.max(260, Math.min(360, (int) (stageW * 0.52f)));
        int poolPanelH = stageH - 24;
        int poolPanelX = stageX + stageW - poolPanelW - 16;
        int poolPanelY = stageY + 12;

        if (mouseX >= poolPanelX && mouseX <= poolPanelX + poolPanelW && mouseY >= poolPanelY && mouseY <= poolPanelY + poolPanelH) {
            SyncBannerDataPayload.ClientBannerInfo current = getCurrentBanner();
            int matchingCount = 0;
            for (SyncBannerDataPayload.BannerItemData item : current.items()) {
                if (poolFilter == 0 || poolFilter == item.stars()) matchingCount++;
            }
            int totalRows = (matchingCount + 1) / 2;
            int filterY = poolPanelY + 26 + 6;
            int contentY = filterY + 16 + 6;
            int contentH = poolPanelH - (contentY - poolPanelY) - 6;
            int visibleRows = Math.max(1, contentH / 32);
            int maxScroll = Math.max(0, totalRows - visibleRows);
            if (maxScroll > 0) {
                if (scrollY > 0) {
                    poolScrollOffset = Math.max(0, poolScrollOffset - 1);
                    return true;
                } else if (scrollY < 0) {
                    poolScrollOffset = Math.min(maxScroll, poolScrollOffset + 1);
                    return true;
                }
            }
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
        this.hoveredPoolStack = ItemStack.EMPTY;

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

        // Pity & Guarantee Status Badge Pill
        int pity5 = ClientGachaData.getPityForType(current.type());
        boolean guaranteed = ClientGachaData.isGuaranteed() && "FEATURED_RESONATOR".equalsIgnoreCase(current.type());
        int badgePityY = bannerTitleY + 30;
        int badgePityX = stageX + 24;

        String pityText = "5★ Pity: " + pity5 + " / 80";
        if (guaranteed) {
            pityText += " §6[Guaranteed Featured]";
        }
        int pityTextW = this.font.width(pityText);
        guiGraphics.fill(badgePityX, badgePityY, badgePityX + pityTextW + 16, badgePityY + 18, 0xCC121224);
        guiGraphics.fill(badgePityX + 1, badgePityY + 1, badgePityX + pityTextW + 15, badgePityY + 17, 0xCC1A1A32);
        guiGraphics.drawString(this.font, pityText, badgePityX + 8, badgePityY + 5, 0xFFFFD700, true);

        // --- BANNER ITEM POOL SHOWCASE (Right Side of Stage) ---
        renderBannerItemPool(guiGraphics, current, stageX, stageY, stageW, stageH, mouseX, mouseY);

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

        // Native Minecraft tooltip for hovered pool item
        if (activeDetailsModal == null && activeHistoryModal == null && activeMailboxModal == null
                && activeItemAddEditModal == null && activeItemPoolModal == null && activeBannerSettingsModal == null) {
            if (hoveredPoolStack != null && !hoveredPoolStack.isEmpty()) {
                guiGraphics.renderTooltip(this.font, hoveredPoolStack, mouseX, mouseY);
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

    private void renderBannerItemPool(GuiGraphics guiGraphics, SyncBannerDataPayload.ClientBannerInfo current, int stageX, int stageY, int stageW, int stageH, int mouseX, int mouseY) {
        int poolPanelW = Math.max(260, Math.min(360, (int) (stageW * 0.52f)));
        int poolPanelH = stageH - 24;
        int poolPanelX = stageX + stageW - poolPanelW - 16;
        int poolPanelY = stageY + 12;

        // Container Outer Border & Dark Glass Box
        guiGraphics.fill(poolPanelX, poolPanelY, poolPanelX + poolPanelW, poolPanelY + poolPanelH, 0xFF2A2448);
        guiGraphics.fill(poolPanelX + 1, poolPanelY + 1, poolPanelX + poolPanelW - 1, poolPanelY + poolPanelH - 1, 0xEE121224);

        // Header Bar
        int headerH = 26;
        guiGraphics.fill(poolPanelX + 1, poolPanelY + 1, poolPanelX + poolPanelW - 1, poolPanelY + headerH, 0xFF1C1635);
        guiGraphics.fill(poolPanelX + 1, poolPanelY + headerH, poolPanelX + poolPanelW - 1, poolPanelY + headerH + 1, 0xFF3D2C5E);

        // Header Title
        guiGraphics.drawString(this.font, "✦ BANNER ITEMS // รายการไอเทม", poolPanelX + 8, poolPanelY + 9, 0xFFFFD166, true);
        String countBadge = current.items().size() + " Items";
        int badgeW = this.font.width(countBadge) + 8;
        int badgeX = poolPanelX + poolPanelW - badgeW - 8;
        guiGraphics.fill(badgeX, poolPanelY + 6, badgeX + badgeW, poolPanelY + 20, 0xCC2A1C40);
        guiGraphics.drawString(this.font, countBadge, badgeX + 4, poolPanelY + 9, 0xFFC77DFF, true);

        // Filter Pills Row
        int filterY = poolPanelY + headerH + 6;
        int filterH = 16;
        renderFilterPill(guiGraphics, poolPanelX + 8, filterY, 40, filterH, "All", poolFilter == 0, mouseX, mouseY);
        renderFilterPill(guiGraphics, poolPanelX + 52, filterY, 36, filterH, "5★", poolFilter == 5, mouseX, mouseY);
        renderFilterPill(guiGraphics, poolPanelX + 92, filterY, 36, filterH, "4★", poolFilter == 4, mouseX, mouseY);
        renderFilterPill(guiGraphics, poolPanelX + 132, filterY, 36, filterH, "3★", poolFilter == 3, mouseX, mouseY);

        // Helper instruction text
        String hintText = "§8• §7ชี้เมาส์เพื่อดูข้อมูลไอเทม";
        guiGraphics.drawString(this.font, hintText, poolPanelX + poolPanelW - this.font.width(hintText) - 8, filterY + 4, 0xFFA0A0C0, true);

        // Content Area
        int contentX = poolPanelX + 8;
        int contentY = filterY + filterH + 6;
        int contentW = poolPanelW - 16;
        int contentH = poolPanelH - (contentY - poolPanelY) - 6;

        // Collect and filter items
        List<SyncBannerDataPayload.BannerItemData> allItems = current.items();
        List<SyncBannerDataPayload.BannerItemData> displayList = new ArrayList<>();
        for (SyncBannerDataPayload.BannerItemData item : allItems) {
            if (poolFilter == 0 || poolFilter == item.stars()) {
                displayList.add(item);
            }
        }

        // Sort: 5-star first, then 4-star, then 3-star. Within same star: rateUp first!
        displayList.sort((a, b) -> {
            if (a.stars() != b.stars()) return Integer.compare(b.stars(), a.stars());
            if (a.isRateUp() != b.isRateUp()) return b.isRateUp() ? 1 : -1;
            return Integer.compare(b.weight(), a.weight());
        });

        if (displayList.isEmpty()) {
            String empty = allItems.isEmpty() ? "No items configured in this banner." : "No items match filter.";
            guiGraphics.drawString(this.font, empty, contentX + (contentW - this.font.width(empty)) / 2, contentY + contentH / 2 - 4, 0xFF888888, true);
            return;
        }

        int cardGap = 4;
        int cardW = (contentW - cardGap - 6) / 2;
        int cardH = 28;
        int rowH = cardH + 4;
        int totalRows = (displayList.size() + 1) / 2;
        int visibleRows = Math.max(1, contentH / rowH);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        poolScrollOffset = Math.max(0, Math.min(poolScrollOffset, maxScroll));

        boolean canInteract = (activeDetailsModal == null && activeHistoryModal == null && activeMailboxModal == null
                && activeItemAddEditModal == null && activeItemPoolModal == null && activeBannerSettingsModal == null);

        for (int r = 0; r < visibleRows && (r + poolScrollOffset) < totalRows; r++) {
            int rowIdx = r + poolScrollOffset;
            int cardY = contentY + r * rowH;

            for (int col = 0; col < 2; col++) {
                int itemIdx = rowIdx * 2 + col;
                if (itemIdx >= displayList.size()) break;

                SyncBannerDataPayload.BannerItemData item = displayList.get(itemIdx);
                int cardX = contentX + col * (cardW + cardGap);

                boolean hovered = canInteract && mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;

                ItemStack stack = getCachedStack(item);

                // Colors based on rarity
                int borderColor;
                int bgBase;
                int textColor;
                if (item.stars() >= 5) {
                    borderColor = 0xFFFFB703; // Gold
                    bgBase = 0xCC2A1B07;
                    textColor = 0xFFFFD166;
                } else if (item.stars() == 4) {
                    borderColor = 0xFFC77DFF; // Purple
                    bgBase = 0xCC200F2E;
                    textColor = 0xFFE0AAFF;
                } else {
                    borderColor = 0xFF4CC9F0; // Cyan
                    bgBase = 0xCC0D1C2A;
                    textColor = 0xFFE0FBFC;
                }

                // Render Card Box
                int bg = hovered ? 0xEE30264E : bgBase;
                int outline = hovered ? 0xFFFFFFFF : borderColor;
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, outline);
                guiGraphics.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, bg);

                // Item Slot Frame
                int slotX = cardX + 2;
                int slotY = cardY + 2;
                int slotSize = 24;
                guiGraphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, borderColor);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, 0xCC080812);

                // Item Icon & Count
                guiGraphics.renderItem(stack, slotX + 4, slotY + 4);
                guiGraphics.renderItemDecorations(this.font, stack, slotX + 4, slotY + 4);

                // Item Display Name
                String name = (item.customName() != null && !item.customName().isEmpty())
                        ? item.customName()
                        : stack.getHoverName().getString();
                int maxNameW = cardW - 34;
                if (item.isRateUp()) maxNameW -= 20;
                if (this.font.width(name) > maxNameW) {
                    while (name.length() > 3 && this.font.width(name + "..") > maxNameW) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name += "..";
                }
                guiGraphics.drawString(this.font, name, cardX + 29, cardY + 4, textColor, true);

                // Stars rating & subtext
                String starStr = item.stars() + "★";
                guiGraphics.drawString(this.font, starStr, cardX + 29, cardY + 15, borderColor, true);

                // Rate-Up Badge
                if (item.isRateUp()) {
                    int upW = 18;
                    int upH = 9;
                    int upX = cardX + cardW - upW - 3;
                    int upY = cardY + 3;
                    guiGraphics.fill(upX, upY, upX + upW, upY + upH, 0xFFFFB703);
                    guiGraphics.drawString(this.font, "UP", upX + 3, upY + 1, 0xFF000000, false);
                }

                // If hovered, capture for tooltip
                if (hovered) {
                    hoveredPoolStack = stack;
                }
            }
        }

        // Sleek Scrollbar
        if (totalRows > visibleRows) {
            int scrollbarX = poolPanelX + poolPanelW - 5;
            int scrollbarY = contentY;
            int scrollbarH = contentH;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarH, 0x44000000);
            int thumbH = Math.max(12, (visibleRows * scrollbarH) / totalRows);
            int thumbY = scrollbarY + (poolScrollOffset * (scrollbarH - thumbH)) / maxScroll;
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xFF8E7DBE);
        }
    }

    private void renderFilterPill(GuiGraphics guiGraphics, int x, int y, int w, int h, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = selected ? 0xFF4A3572 : (hovered ? 0xFF28243C : 0xFF181528);
        int border = selected ? 0xFFFFD166 : (hovered ? 0xFFAAAAAA : 0xFF352B4E);
        int text = selected ? 0xFFFFD166 : (hovered ? 0xFFFFFFFF : 0xFFB8B8D4);
        guiGraphics.fill(x, y, x + w, y + h, border);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int textW = this.font.width(label);
        guiGraphics.drawString(this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, text, true);
    }

    private ItemStack getCachedStack(SyncBannerDataPayload.BannerItemData item) {
        String key = item.itemId() + "#" + item.count() + "#" + (item.customName() != null ? item.customName() : "") + "#" + (item.snbt() != null ? item.snbt() : "");
        return itemStackCache.computeIfAbsent(key, k -> {
            ItemStack stack = ItemStack.EMPTY;
            HolderLookup.Provider registries = (Minecraft.getInstance().level != null) ? Minecraft.getInstance().level.registryAccess() : null;
            if (item.snbt() != null && !item.snbt().trim().isEmpty() && registries != null) {
                try {
                    CompoundTag tag = TagParser.parseTag(item.snbt());
                    stack = ItemStack.parseOptional(registries, tag);
                } catch (Exception ignored) {}
            }
            if (stack.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.parse(item.itemId());
                    var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                    if (itemOpt.isPresent()) {
                        stack = new ItemStack(itemOpt.get(), Math.max(1, item.count()));
                    }
                } catch (Exception ignored) {}
            }
            if (stack.isEmpty()) {
                stack = new ItemStack(Items.DIRT, Math.max(1, item.count()));
            }
            if (item.customName() != null && !item.customName().trim().isEmpty()) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(item.customName()));
            }
            return stack;
        });
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
    public void onClose() {
        itemStackCache.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
