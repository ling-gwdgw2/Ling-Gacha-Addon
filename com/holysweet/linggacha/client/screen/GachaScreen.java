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

    private final Map<String, ItemStack> itemStackCache = new HashMap<>();
    private Button viewPoolStageBtn;

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

        // Stage View Pool Button
        int stageX = sidebarWidth;
        int stageY = headerHeight;
        int textOffsetAdmin = editMode ? 26 : 0;
        int bannerTitleY = stageY + 20 + textOffsetAdmin;
        int badgePityY = bannerTitleY + 30;

        SyncBannerDataPayload.ClientBannerInfo currentBanner = getCurrentBanner();
        this.viewPoolStageBtn = Button.builder(
                Component.literal("✦ View Items (" + currentBanner.items().size() + ")"),
                b -> openDetails())
                .bounds(stageX + 24, badgePityY + 24, 118, 20).build();
        this.addRenderableWidget(viewPoolStageBtn);

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
        if (viewPoolStageBtn != null) {
            int textOffsetAdmin = editMode ? 26 : 0;
            int bannerTitleY = headerHeight + 20 + textOffsetAdmin;
            int badgePityY = bannerTitleY + 30;
            viewPoolStageBtn.setY(badgePityY + 24);
        }
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
        if (viewPoolStageBtn != null) {
            viewPoolStageBtn.setMessage(Component.literal("✦ View Items (" + banner.items().size() + ")"));
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
        if (activeDetailsModal != null) {
            return activeDetailsModal.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
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

        // Keep stage button visibility synced
        if (viewPoolStageBtn != null) {
            viewPoolStageBtn.visible = (activeDetailsModal == null && activeHistoryModal == null && activeMailboxModal == null
                    && activeItemAddEditModal == null && activeItemPoolModal == null && activeBannerSettingsModal == null);
        }

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

    public ItemStack getCachedStack(SyncBannerDataPayload.BannerItemData item) {
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
