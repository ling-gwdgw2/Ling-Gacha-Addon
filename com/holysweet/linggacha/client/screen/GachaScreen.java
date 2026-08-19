package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.network.AdminSyncItemPoolPayload;
import com.holysweet.linggacha.network.PullConvenePayload;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import com.holysweet.questshop.client.ClientCoins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class GachaScreen extends Screen {

    private int imageWidth = 360;
    private int imageHeight = 230;
    private int leftPos;
    private int topPos;

    private int selectedBannerIndex = 0;

    // Normal User Controls
    private Button pull1Btn;
    private Button pull10Btn;
    private Button detailsBtn;

    // Admin / OP Controls
    private boolean editMode = false;
    private Button editModeToggleBtn;
    private Button bannerSettingsBtn;
    private Button poolManagerBtn;
    private Button newBannerBtn;

    // Active Modals
    private GachaDetailsModal activeDetailsModal = null;
    private BannerSettingsModal activeBannerSettingsModal = null;
    private ItemPoolEditModal activeItemPoolModal = null;
    private ItemAddEditModal activeItemAddEditModal = null;

    public GachaScreen() {
        super(Component.literal("Convene / Gacha"));
    }

    public int getGuiLeftPos() {
        return leftPos;
    }

    public int getGuiTopPos() {
        return topPos;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int btnY = this.topPos + this.imageHeight - 26;

        this.detailsBtn = Button.builder(Component.literal("Details"), b -> openDetails())
                .bounds(this.leftPos + 12, btnY, 65, 20).build();
        this.addRenderableWidget(detailsBtn);

        this.pull1Btn = Button.builder(Component.literal("Convene 1x"), b -> pull(1))
                .bounds(this.leftPos + this.imageWidth - 265, btnY, 125, 20).build();
        this.addRenderableWidget(pull1Btn);

        this.pull10Btn = Button.builder(Component.literal("Convene 10x"), b -> pull(10))
                .bounds(this.leftPos + this.imageWidth - 135, btnY, 125, 20).build();
        this.addRenderableWidget(pull10Btn);

        // Admin Controls (OP or Creative Mode)
        boolean canAdmin = false;
        if (Minecraft.getInstance().player != null) {
            canAdmin = Minecraft.getInstance().player.hasPermissions(2) || Minecraft.getInstance().player.isCreative();
        }

        if (canAdmin) {
            this.editModeToggleBtn = Button.builder(Component.literal(editMode ? "§c[Exit Edit]" : "§e[⚙ Edit Mode]"), b -> toggleEditMode())
                    .bounds(this.leftPos - 110, this.topPos + this.imageHeight - 24, 105, 18).build();
            this.addRenderableWidget(editModeToggleBtn);

            int adminBtnY = this.topPos + 32;
            this.bannerSettingsBtn = Button.builder(Component.literal("§e⚙ Settings"), b -> openBannerSettingsModal(getCurrentBanner(), false))
                    .bounds(this.leftPos + this.imageWidth - 195, adminBtnY, 80, 16).build();
            this.bannerSettingsBtn.visible = editMode;
            this.addRenderableWidget(bannerSettingsBtn);

            this.poolManagerBtn = Button.builder(Component.literal("§b🗂 Pool (" + getCurrentBanner().totalItems() + ")"), b -> openItemPoolModal(getCurrentBanner().id(), getCurrentBanner().title()))
                    .bounds(this.leftPos + this.imageWidth - 110, adminBtnY, 100, 16).build();
            this.poolManagerBtn.visible = editMode;
            this.addRenderableWidget(poolManagerBtn);

            this.newBannerBtn = Button.builder(Component.literal("§a+ New Banner"), b -> createNewBanner())
                    .bounds(this.leftPos - 110, this.topPos - 20, 105, 16).build();
            this.newBannerBtn.visible = editMode;
            this.addRenderableWidget(newBannerBtn);
        }

        updateButtons();
    }

    private void toggleEditMode() {
        this.editMode = !editMode;
        if (editModeToggleBtn != null) {
            editModeToggleBtn.setMessage(Component.literal(editMode ? "§c[Exit Edit]" : "§e[⚙ Edit Mode]"));
        }
        if (bannerSettingsBtn != null) bannerSettingsBtn.visible = editMode;
        if (poolManagerBtn != null) poolManagerBtn.visible = editMode;
        if (newBannerBtn != null) newBannerBtn.visible = editMode;
    }

    public void refreshData() {
        updateButtons();
        if (poolManagerBtn != null) {
            poolManagerBtn.setMessage(Component.literal("§b🗂 Pool (" + getCurrentBanner().totalItems() + ")"));
        }
    }

    public void onItemPoolSynced(AdminSyncItemPoolPayload payload) {
        if (activeItemPoolModal != null) {
            activeItemPoolModal.updateItems(payload.items());
        }
    }

    private void openDetails() {
        closeModal();
        this.activeDetailsModal = new GachaDetailsModal(this, getCurrentBanner());
        this.activeDetailsModal.init(leftPos, topPos);
    }

    public void openBannerSettingsModal(SyncBannerDataPayload.ClientBannerInfo banner, boolean isNew) {
        closeModal();
        this.activeBannerSettingsModal = new BannerSettingsModal(this, banner, isNew);
        this.activeBannerSettingsModal.init(leftPos, topPos);
    }

    public void openItemPoolModal(String bannerId, String bannerTitle) {
        closeModal();
        this.activeItemPoolModal = new ItemPoolEditModal(this, bannerId, bannerTitle);
        this.activeItemPoolModal.init(leftPos, topPos);
    }

    public void openItemAddModal(String bannerId, ItemStack heldStack) {
        this.activeItemAddEditModal = new ItemAddEditModal(this, bannerId, heldStack);
        this.activeItemAddEditModal.init(leftPos, topPos);
    }

    public void openItemEditModal(String bannerId, AdminSyncItemPoolPayload.ItemEntryData itemData) {
        this.activeItemAddEditModal = new ItemAddEditModal(this, bannerId, itemData);
        this.activeItemAddEditModal.init(leftPos, topPos);
    }

    public void closeModal() {
        this.activeDetailsModal = null;
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
                newId, "New Banner", "Custom Pool", "FEATURED_RESONATOR", 160, 0, "minecraft:diamond_sword", 0
        );
        openBannerSettingsModal(dummy, true);
    }

    private SyncBannerDataPayload.ClientBannerInfo getCurrentBanner() {
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        if (banners.isEmpty()) {
            return new SyncBannerDataPayload.ClientBannerInfo("default", "Convene", "Convene Pool", "STANDARD", 160, 0, "minecraft:netherite_sword", 0);
        }
        int idx = Math.max(0, Math.min(banners.size() - 1, selectedBannerIndex));
        return banners.get(idx);
    }

    private void pull(int count) {
        SyncBannerDataPayload.ClientBannerInfo banner = getCurrentBanner();
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
        if (poolManagerBtn != null) {
            poolManagerBtn.setMessage(Component.literal("§b🗂 Pool (" + banner.totalItems() + ")"));
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

        // Check Banner Sidebar tabs
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        int tabX = this.leftPos - 110;
        int tabY = this.topPos + 8;
        int tabW = 105;
        int tabH = 22;

        for (int i = 0; i < banners.size(); i++) {
            int y = tabY + i * (tabH + 4);
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH) {
                this.selectedBannerIndex = i;
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
        // Main Container Background (Solid Opaque Dark Wuthering Sci-Fi Theme)
        guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFF0D0D18);
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + this.imageHeight - 1, 0xFF141422);

        // Sidebar Background
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        int tabX = this.leftPos - 110;
        int tabY = this.topPos + 8;
        int tabW = 105;
        int tabH = 22;

        guiGraphics.fill(tabX - 2, this.topPos, this.leftPos - 2, this.topPos + this.imageHeight, 0xFF0D0D18);
        guiGraphics.fill(tabX - 1, this.topPos + 1, this.leftPos - 3, this.topPos + this.imageHeight - 1, 0xFF18182A);

        // Render Sidebar Tabs
        for (int i = 0; i < banners.size(); i++) {
            SyncBannerDataPayload.ClientBannerInfo b = banners.get(i);
            int y = tabY + i * (tabH + 4);
            boolean selected = (i == selectedBannerIndex);
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH;

            int bg = selected ? 0xFF3D3060 : (hovered ? 0xFF2A2A44 : 0xFF1C1C2E);
            guiGraphics.fill(tabX, y, tabX + tabW, y + tabH, bg);
            if (selected) {
                guiGraphics.fill(tabX, y, tabX + 3, y + tabH, 0xFFFFB703); // Golden accent bar
            }

            int textColor = selected ? 0xFFFFD166 : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA);
            guiGraphics.drawString(this.font, b.title(), tabX + 8, y + 7, textColor, true);
        }

        // Top Header Bar
        guiGraphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.imageWidth - 1, this.topPos + 22, 0xFF1F1F34);

        // Screen Title
        guiGraphics.drawString(this.font, "CONVENE // 唤取", this.leftPos + 10, this.topPos + 7, 0xFFEEEEEE, true);

        // Currency Badges (Primogems & Afterglow Corals)
        String coinsText = "Primogems: " + ClientCoins.get() + " G";
        int coinsW = this.font.width(coinsText);
        int coinBadgeX = this.leftPos + this.imageWidth - coinsW - 12;
        guiGraphics.fill(coinBadgeX - 4, this.topPos + 4, coinBadgeX + coinsW + 6, this.topPos + 18, 0xFF3D3000);
        guiGraphics.drawString(this.font, coinsText, coinBadgeX, this.topPos + 7, 0xFFFFD700, true);

        String coralsText = "Corals: " + ClientGachaData.getCorals();
        int coralsW = this.font.width(coralsText);
        int coralBadgeX = coinBadgeX - coralsW - 16;
        guiGraphics.fill(coralBadgeX - 4, this.topPos + 4, coralBadgeX + coralsW + 6, this.topPos + 18, 0xFF351A4A);
        guiGraphics.drawString(this.font, coralsText, coralBadgeX, this.topPos + 7, 0xFFC77DFF, true);

        // Banner Content Area
        SyncBannerDataPayload.ClientBannerInfo current = getCurrentBanner();
        int contentX = this.leftPos + 12;
        int contentY = this.topPos + 30;
        int contentW = this.imageWidth - 24;
        int contentH = this.imageHeight - 64;

        // Banner Card Background
        guiGraphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0xFF1A1A2E);
        guiGraphics.fill(contentX + 1, contentY + 1, contentX + contentW - 1, contentY + contentH - 1, 0xFF22223C);

        // Banner Title & Subtitle
        guiGraphics.drawString(this.font, "✦ " + current.title().toUpperCase() + " ✦", contentX + 14, contentY + 12, 0xFFFFD166, true);
        guiGraphics.drawString(this.font, current.subtitle(), contentX + 14, contentY + 25, 0xFFB0B0CC, true);

        // Featured Preview Item Rendering
        ItemStack previewStack = getPreviewStack(current.previewItem());
        int itemBoxX = contentX + contentW - 75;
        int itemBoxY = contentY + 18;
        guiGraphics.fill(itemBoxX - 4, itemBoxY - 4, itemBoxX + 54, itemBoxY + 54, 0xFF3B2F55);
        guiGraphics.fill(itemBoxX - 3, itemBoxY - 3, itemBoxX + 53, itemBoxY + 53, 0xFF27203A);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(itemBoxX + 9, itemBoxY + 9, 0);
        guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
        guiGraphics.renderItem(previewStack, 0, 0);
        guiGraphics.pose().popPose();

        guiGraphics.drawString(this.font, "5★ Rate-Up Item", itemBoxX - 10, itemBoxY + 56, 0xFFFFB703, true);

        // Pity Tracker Box
        int pityBoxX = contentX + 14;
        int pityBoxY = contentY + 55;
        int pityBoxW = 200;
        int pityBoxH = 65;

        guiGraphics.fill(pityBoxX, pityBoxY, pityBoxX + pityBoxW, pityBoxY + pityBoxH, 0xFF141424);
        guiGraphics.fill(pityBoxX + 1, pityBoxY + 1, pityBoxX + pityBoxW - 1, pityBoxY + pityBoxH - 1, 0xFF1C1C30);

        int currentPity = ClientGachaData.getPityForType(current.type());
        guiGraphics.drawString(this.font, "5★ Pity Counter:", pityBoxX + 8, pityBoxY + 8, 0xFFEEEEEE, true);

        // Pity progress bar
        int barW = 180;
        int filledW = Math.min(barW, (currentPity * barW) / 80);
        guiGraphics.fill(pityBoxX + 8, pityBoxY + 22, pityBoxX + 8 + barW, pityBoxY + 30, 0xFF0D0D18);
        int barColor = currentPity >= 65 ? 0xFFFFB703 : 0xFF4361EE;
        guiGraphics.fill(pityBoxX + 8, pityBoxY + 22, pityBoxX + 8 + filledW, pityBoxY + 30, barColor);

        guiGraphics.drawString(this.font, currentPity + " / 80 (Hard Pity at 80)", pityBoxX + 8, pityBoxY + 34, 0xFFAAAAAA, true);

        // 50/50 Guarantee Status
        if (current.type().equals("FEATURED_RESONATOR")) {
            if (ClientGachaData.isGuaranteed()) {
                guiGraphics.drawString(this.font, "★ 50/50 STATUS: NEXT 5★ GUARANTEED FEATURED!", pityBoxX + 8, pityBoxY + 48, 0xFFFFD700, true);
            } else {
                guiGraphics.drawString(this.font, "★ 50/50 STATUS: 50% Featured / 50% Standard", pityBoxX + 8, pityBoxY + 48, 0xFF55FF55, true);
            }
        } else if (current.type().equals("FEATURED_WEAPON")) {
            guiGraphics.drawString(this.font, "★ WEAPON BANNER: 100% GUARANTEED RATE-UP!", pityBoxX + 8, pityBoxY + 48, 0xFFFFD700, true);
        } else {
            guiGraphics.drawString(this.font, "★ STANDARD POOL: Standard 5★ Resonators & Weapons", pityBoxX + 8, pityBoxY + 48, 0xFF55FFFF, true);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render Active Modal Overlays
        if (activeDetailsModal != null) {
            activeDetailsModal.render(guiGraphics, mouseX, mouseY, partialTick, leftPos, topPos);
        } else if (activeItemAddEditModal != null) {
            activeItemAddEditModal.render(guiGraphics, mouseX, mouseY, partialTick, leftPos, topPos);
        } else if (activeItemPoolModal != null) {
            activeItemPoolModal.render(guiGraphics, mouseX, mouseY, partialTick, leftPos, topPos);
        } else if (activeBannerSettingsModal != null) {
            activeBannerSettingsModal.render(guiGraphics, mouseX, mouseY, partialTick, leftPos, topPos);
        }
    }

    private ItemStack getPreviewStack(String itemId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
            if (itemOpt.isPresent()) {
                return new ItemStack(itemOpt.get(), 1);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.NETHERITE_SWORD, 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
