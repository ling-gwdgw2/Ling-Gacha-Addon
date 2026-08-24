package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.network.RequestPullHistoryPayload;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import com.holysweet.linggacha.network.SyncPullHistoryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GachaHistoryModal {

    private final GachaScreen parent;
    private final String initialBannerId;
    private String currentFilter;

    private final List<SyncPullHistoryPayload.HistoryRecordData> records = new ArrayList<>();
    private int current5StarPity = 0;
    private int current4StarPity = 0;
    private boolean isGuaranteed = false;
    private int totalPulls = 0;

    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 6;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    private Button thisBannerBtn;
    private Button cycleBannerBtn;
    private Button allFilterBtn;
    private Button prevBtn;
    private Button nextBtn;
    private Button closeBtn;

    public GachaHistoryModal(GachaScreen parent, String initialBannerId) {
        this.parent = parent;
        this.initialBannerId = (initialBannerId != null && !initialBannerId.isEmpty()) ? initialBannerId : "featured_character";
        // Default strictly to the banner currently open
        this.currentFilter = this.initialBannerId;
    }

    public void init(int screenWidth, int screenHeight) {
        int modalW = 380;
        int modalH = 240;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        int filterY = modalY + 24;

        // Button 1: Dedicated button for current banner
        String thisTitle = getBannerTitle(initialBannerId);
        if (thisTitle.length() > 11) thisTitle = thisTitle.substring(0, 9) + "..";
        this.thisBannerBtn = Button.builder(Component.literal(currentFilter.equalsIgnoreCase(initialBannerId) ? "§6[" + thisTitle + "]" : thisTitle), b -> switchFilter(initialBannerId))
                .bounds(modalX + 12, filterY, 85, 16).build();

        // Button 2: Cycle across all other banners
        this.cycleBannerBtn = Button.builder(Component.literal("§e" + getCycleButtonLabel() + " v"), b -> cycleFilter())
                .bounds(modalX + 102, filterY, 115, 16).build();

        // Button 3: All Pulls summary
        this.allFilterBtn = Button.builder(Component.literal(currentFilter.equalsIgnoreCase("ALL") ? "§6[All Pulls]" : "All Pulls"), b -> switchFilter("ALL"))
                .bounds(modalX + 222, filterY, 65, 16).build();

        int bottomY = modalY + modalH - 24;
        this.prevBtn = Button.builder(Component.literal("< Prev"), b -> prevPage())
                .bounds(modalX + 12, bottomY, 65, 18).build();

        this.nextBtn = Button.builder(Component.literal("Next >"), b -> nextPage())
                .bounds(modalX + modalW - 77 - 80, bottomY, 65, 18).build();

        this.closeBtn = Button.builder(Component.literal("Close"), b -> parent.closeModal())
                .bounds(modalX + modalW - 72, bottomY, 60, 18).build();

        // Request initial history for this specific banner
        requestHistory();
    }

    public void updateData(SyncPullHistoryPayload payload) {
        this.records.clear();
        this.records.addAll(payload.records());
        this.current5StarPity = payload.current5StarPity();
        this.current4StarPity = payload.current4StarPity();
        this.isGuaranteed = payload.isGuaranteed();
        this.totalPulls = payload.totalPulls();
        this.currentPage = 0;
        updateButtonLabels();
    }

    private String getCycleButtonLabel() {
        if (currentFilter.equalsIgnoreCase("ALL")) return "Banner: All";
        String name = getBannerTitle(currentFilter);
        if (name.length() > 10) name = name.substring(0, 8) + "..";
        return "Pool: " + name;
    }

    private void updateButtonLabels() {
        if (thisBannerBtn != null) {
            String thisTitle = getBannerTitle(initialBannerId);
            if (thisTitle.length() > 11) thisTitle = thisTitle.substring(0, 9) + "..";
            thisBannerBtn.setMessage(Component.literal(currentFilter.equalsIgnoreCase(initialBannerId) ? "§6[" + thisTitle + "]" : thisTitle));
        }
        if (cycleBannerBtn != null) {
            cycleBannerBtn.setMessage(Component.literal("§e" + getCycleButtonLabel() + " ▾"));
        }
        if (allFilterBtn != null) {
            allFilterBtn.setMessage(Component.literal(currentFilter.equalsIgnoreCase("ALL") ? "§6[All Pulls]" : "All Pulls"));
        }
    }

    private void cycleFilter() {
        List<SyncBannerDataPayload.ClientBannerInfo> banners = ClientGachaData.getBanners();
        List<String> filterList = new ArrayList<>();
        for (SyncBannerDataPayload.ClientBannerInfo b : banners) {
            filterList.add(b.id());
        }
        filterList.add("ALL");

        int currentIdx = 0;
        for (int i = 0; i < filterList.size(); i++) {
            if (filterList.get(i).equalsIgnoreCase(currentFilter)) {
                currentIdx = i;
                break;
            }
        }

        int nextIdx = (currentIdx + 1) % filterList.size();
        switchFilter(filterList.get(nextIdx));
    }

    private void switchFilter(String filter) {
        this.currentFilter = filter;
        updateButtonLabels();
        requestHistory();
    }

    private void requestHistory() {
        PacketDistributor.sendToServer(new RequestPullHistoryPayload(currentFilter));
    }

    private void prevPage() {
        if (currentPage > 0) currentPage--;
    }

    private void nextPage() {
        int maxPages = Math.max(1, (int) Math.ceil((double) records.size() / ITEMS_PER_PAGE));
        if (currentPage < maxPages - 1) currentPage++;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        int modalW = 380;
        int modalH = 240;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // Full Screen Dark Dim Backdrop
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

        // Solid Dark Modal Box
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF080812);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Header Title (Displays active banner name)
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 20, 0xFF221C38);
        String header = "HISTORY: " + (currentFilter.equalsIgnoreCase("ALL") ? "ALL CONVENES" : getBannerTitle(currentFilter).toUpperCase());
        if (Minecraft.getInstance().font.width(header) > modalW - 14) {
            header = "CONVENE HISTORY";
        }
        guiGraphics.drawString(Minecraft.getInstance().font, header, modalX + 10, modalY + 6, 0xFFFFD700, true);

        // Summary Info Pill on the right side of header
        String pitySummary = currentFilter.equalsIgnoreCase("ALL") ?
                "Total Pulls: §f" + totalPulls :
                "5-Star: §6" + current5StarPity + "§7/80 | Pulls: §f" + totalPulls;
        guiGraphics.drawString(Minecraft.getInstance().font, pitySummary, modalX + modalW - Minecraft.getInstance().font.width(pitySummary) - 10, modalY + 28, 0xFFC8C8E0, true);

        // Filter Buttons
        if (thisBannerBtn != null) thisBannerBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (cycleBannerBtn != null) cycleBannerBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (allFilterBtn != null) allFilterBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        // History Table Container
        int tableX = modalX + 10;
        int tableY = modalY + 44;
        int tableW = modalW - 20;
        int tableH = 142;

        guiGraphics.fill(tableX, tableY, tableX + tableW, tableY + tableH, 0xFF0A0A16);
        guiGraphics.fill(tableX + 1, tableY + 1, tableX + tableW - 1, tableY + tableH - 1, 0xFF161628);

        // Table Header
        guiGraphics.fill(tableX + 1, tableY + 1, tableX + tableW - 1, tableY + 15, 0xFF1E1E34);
        guiGraphics.drawString(Minecraft.getInstance().font, "Star", tableX + 5, tableY + 4, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Item Name", tableX + 38, tableY + 4, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Banner Pool", tableX + 160, tableY + 4, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Pity #", tableX + 250, tableY + 4, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Time", tableX + 305, tableY + 4, 0xFFAAAAAA, true);

        // Render Table Rows
        int rowH = 20;
        int startIdx = currentPage * ITEMS_PER_PAGE;
        int maxPages = Math.max(1, (int) Math.ceil((double) records.size() / ITEMS_PER_PAGE));

        if (records.isEmpty()) {
            String emptyMsg = "No pull history recorded for this banner yet.";
            guiGraphics.drawString(Minecraft.getInstance().font, emptyMsg, tableX + tableW / 2 - Minecraft.getInstance().font.width(emptyMsg) / 2, tableY + 60, 0xFF888888, true);
        } else {
            for (int i = 0; i < ITEMS_PER_PAGE && (startIdx + i) < records.size(); i++) {
                SyncPullHistoryPayload.HistoryRecordData entry = records.get(startIdx + i);
                int rowY = tableY + 16 + i * rowH;

                boolean hovered = mouseX >= tableX && mouseX <= tableX + tableW && mouseY >= rowY && mouseY <= rowY + rowH - 1;
                int rowBg = hovered ? 0xFF2A2A44 : (i % 2 == 0 ? 0xFF1A1A2E : 0xFF141424);
                guiGraphics.fill(tableX + 2, rowY, tableX + tableW - 2, rowY + rowH - 1, rowBg);

                // Star Badge
                int starColor = entry.stars() >= 5 ? 0xFFFFB703 : (entry.stars() == 4 ? 0xFFC77DFF : 0xFF4CC9F0);
                String starTag = entry.stars() + "★";
                guiGraphics.drawString(Minecraft.getInstance().font, starTag, tableX + 4, rowY + 6, starColor, true);

                // Item Icon (Small)
                ItemStack stack = getItemStack(entry.itemId());
                guiGraphics.renderItem(stack, tableX + 30, rowY + 2);

                // Item Name
                String name = entry.itemName();
                if (Minecraft.getInstance().font.width(name) > 105) {
                    name = name.substring(0, Math.min(name.length(), 13)) + "..";
                }
                guiGraphics.drawString(Minecraft.getInstance().font, name, tableX + 48, rowY + 6, 0xFFFFFFFF, true);

                // Banner Name
                String bannerName = getBannerTitle(entry.bannerId());
                if (Minecraft.getInstance().font.width(bannerName) > 80) {
                    bannerName = bannerName.substring(0, Math.min(bannerName.length(), 10)) + "..";
                }
                guiGraphics.drawString(Minecraft.getInstance().font, bannerName, tableX + 160, rowY + 6, 0xFFB0B0CC, true);

                // Pity Number
                String pityStr = "Roll #" + entry.pityCount();
                if (entry.stars() >= 5) pityStr = "§6" + pityStr;
                guiGraphics.drawString(Minecraft.getInstance().font, pityStr, tableX + 250, rowY + 6, 0xFFAAAAAA, true);

                // Date Time
                String timeStr = DATE_FORMAT.format(new Date(entry.timestamp()));
                guiGraphics.drawString(Minecraft.getInstance().font, timeStr, tableX + 305, rowY + 6, 0xFF8888AA, true);
            }
        }

        // Bottom Controls
        if (prevBtn != null) {
            prevBtn.active = (currentPage > 0);
            prevBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Page Indicator
        String pageStr = "Page " + (currentPage + 1) + " / " + maxPages;
        int pageStrX = modalX + 140;
        guiGraphics.drawString(Minecraft.getInstance().font, pageStr, pageStrX, modalY + modalH - 19, 0xFFDDDDDD, true);

        if (nextBtn != null) {
            nextBtn.active = (currentPage < maxPages - 1);
            nextBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        if (closeBtn != null) closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    private String getBannerTitle(String bannerId) {
        if (bannerId == null) return "Unknown";
        for (SyncBannerDataPayload.ClientBannerInfo b : ClientGachaData.getBanners()) {
            if (b.id().equalsIgnoreCase(bannerId)) {
                return b.title();
            }
        }
        return bannerId;
    }

    private ItemStack getItemStack(String itemId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var opt = BuiltInRegistries.ITEM.getOptional(rl);
            if (opt.isPresent()) {
                return new ItemStack(opt.get(), 1);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.IRON_INGOT, 1);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (thisBannerBtn != null && thisBannerBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (cycleBannerBtn != null && cycleBannerBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (allFilterBtn != null && allFilterBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (prevBtn != null && prevBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (nextBtn != null && nextBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) return true;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            prevPage();
            return true;
        } else if (scrollY < 0) {
            nextPage();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeModal();
            return true;
        }
        return true;
    }
}
