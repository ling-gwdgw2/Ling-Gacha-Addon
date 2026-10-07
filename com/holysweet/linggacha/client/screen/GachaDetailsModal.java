package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GachaDetailsModal {

    private final GachaScreen parent;
    private final SyncBannerDataPayload.ClientBannerInfo banner;

    // Tabs: 0 = Item Pool, 1 = Rates & Rules
    private int activeTab = 0;

    // Item Pool Filter & Scrolling
    private int poolFilter = 0; // 0 = All, 5 = 5★, 4 = 4★, 3 = 3★
    private int poolScrollOffset = 0;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    private Button closeBtn;

    public GachaDetailsModal(GachaScreen parent, SyncBannerDataPayload.ClientBannerInfo banner) {
        this.parent = parent;
        this.banner = banner;
    }

    public void init(int screenWidth, int screenHeight) {
        int modalW = Math.min(390, screenWidth - 24);
        int modalH = Math.min(250, screenHeight - 24);
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        this.closeBtn = Button.builder(Component.literal("Close"), b -> parent.closeDetails())
                .bounds(modalX + modalW / 2 - 40, modalY + modalH - 23, 80, 18).build();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        int modalW = Math.min(390, screenWidth - 24);
        int modalH = Math.min(250, screenHeight - 24);
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        Font font = Minecraft.getInstance().font;
        this.hoveredStack = ItemStack.EMPTY;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // 1. Full-screen Dark Dim Backdrop
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

        // 2. Modal Outer Border & Dark Sci-Fi Box
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF352B4E);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFA0F0E1C);

        // 3. Title Bar
        int titleBarH = 22;
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + titleBarH, 0xFF1D1730);
        guiGraphics.fill(modalX + 1, modalY + titleBarH, modalX + modalW - 1, modalY + titleBarH + 1, 0xFF3D2C5E);

        String title = "✦ CONVENE DETAILS // " + banner.title();
        if (font.width(title) > modalW - 40) {
            title = font.plainSubstrByWidth(title, modalW - 50) + "..";
        }
        guiGraphics.drawString(font, title, modalX + 10, modalY + 7, 0xFFFFD166, true);

        // Top-right [X] close button
        int closeX = modalX + modalW - 18;
        int closeY = modalY + 4;
        boolean hoverCloseX = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14;
        guiGraphics.fill(closeX, closeY, closeX + 14, closeY + 14, hoverCloseX ? 0xFF8A2434 : 0xFF2A1C30);
        guiGraphics.drawString(font, "X", closeX + 4, closeY + 3, hoverCloseX ? 0xFFFFFFFF : 0xFFAAAAAA, true);

        // 4. Tabs Row
        int tabY = modalY + 25;
        int tabH = 18;
        int tabW = (modalW - 24) / 2;
        int tab0X = modalX + 10;
        int tab1X = tab0X + tabW + 4;

        renderTab(guiGraphics, font, tab0X, tabY, tabW, tabH, "✦ Item Pool (" + banner.items().size() + ")", activeTab == 0, mouseX, mouseY);
        renderTab(guiGraphics, font, tab1X, tabY, tabW, tabH, "ℹ Rates & Rules", activeTab == 1, mouseX, mouseY);

        // 5. Tab Content
        if (activeTab == 0) {
            renderItemPoolTab(guiGraphics, font, modalX, modalY, modalW, modalH, tabY + tabH + 4, mouseX, mouseY);
        } else {
            renderRatesAndRulesTab(guiGraphics, font, modalX, modalY, modalW, modalH, tabY + tabH + 8, mouseX, mouseY);
        }

        // 6. Bottom Close Button
        if (closeBtn != null) {
            closeBtn.setX(modalX + modalW / 2 - 40);
            closeBtn.setY(modalY + modalH - 23);
            closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 7. Render Hovered Stack Tooltip on Top
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }

        guiGraphics.pose().popPose();
    }

    private void renderTab(GuiGraphics guiGraphics, Font font, int x, int y, int w, int h, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = selected ? 0xFF3D2C5E : (hovered ? 0xFF242038 : 0xFF141324);
        int border = selected ? 0xFFFFD166 : (hovered ? 0xFFAAAAAA : 0xFF2D2545);
        int text = selected ? 0xFFFFD166 : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA);

        guiGraphics.fill(x, y, x + w, y + h, border);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int textW = font.width(label);
        guiGraphics.drawString(font, label, x + (w - textW) / 2, y + (h - 8) / 2, text, true);
    }

    private void renderItemPoolTab(GuiGraphics guiGraphics, Font font, int modalX, int modalY, int modalW, int modalH, int startY, int mouseX, int mouseY) {
        // Filter Pills Row
        int filterY = startY;
        int filterH = 15;
        renderFilterPill(guiGraphics, font, modalX + 10, filterY, 36, filterH, "All", poolFilter == 0, mouseX, mouseY);
        renderFilterPill(guiGraphics, font, modalX + 49, filterY, 32, filterH, "5★", poolFilter == 5, mouseX, mouseY);
        renderFilterPill(guiGraphics, font, modalX + 84, filterY, 32, filterH, "4★", poolFilter == 4, mouseX, mouseY);
        renderFilterPill(guiGraphics, font, modalX + 119, filterY, 32, filterH, "3★", poolFilter == 3, mouseX, mouseY);

        // Hint Text on Right
        String hint = "§8• §7Hover over item to view details";
        guiGraphics.drawString(font, hint, modalX + modalW - font.width(hint) - 10, filterY + 4, 0xFFA0A0C0, true);

        // Items Content Area
        int contentX = modalX + 10;
        int contentY = filterY + filterH + 5;
        int contentW = modalW - 20;
        int contentH = modalY + modalH - 26 - contentY;

        // Collect & Filter
        List<SyncBannerDataPayload.BannerItemData> allItems = banner.items();
        List<SyncBannerDataPayload.BannerItemData> displayList = new ArrayList<>();
        for (SyncBannerDataPayload.BannerItemData item : allItems) {
            if (poolFilter == 0 || poolFilter == item.stars()) {
                displayList.add(item);
            }
        }

        // Sort: 5★ > 4★ > 3★, Rate-Up first, then weight
        displayList.sort((a, b) -> {
            if (a.stars() != b.stars()) return Integer.compare(b.stars(), a.stars());
            if (a.isRateUp() != b.isRateUp()) return b.isRateUp() ? 1 : -1;
            return Integer.compare(b.weight(), a.weight());
        });

        if (displayList.isEmpty()) {
            String empty = allItems.isEmpty() ? "No items configured in this banner." : "No items match filter.";
            guiGraphics.drawString(font, empty, contentX + (contentW - font.width(empty)) / 2, contentY + contentH / 2 - 4, 0xFF888888, true);
            return;
        }

        int cardGap = 6;
        int cardW = (contentW - cardGap - 6) / 2;
        int cardH = 28;
        int rowH = cardH + 4;
        int totalRows = (displayList.size() + 1) / 2;
        int visibleRows = Math.max(1, contentH / rowH);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        poolScrollOffset = Math.max(0, Math.min(poolScrollOffset, maxScroll));

        for (int r = 0; r < visibleRows && (r + poolScrollOffset) < totalRows; r++) {
            int rowIdx = r + poolScrollOffset;
            int cardY = contentY + r * rowH;

            for (int col = 0; col < 2; col++) {
                int itemIdx = rowIdx * 2 + col;
                if (itemIdx >= displayList.size()) break;

                SyncBannerDataPayload.BannerItemData item = displayList.get(itemIdx);
                int cardX = contentX + col * (cardW + cardGap);

                boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
                ItemStack stack = parent.getCachedStack(item);

                int borderColor;
                int bgBase;
                int textColor;
                if (item.stars() >= 5) {
                    borderColor = 0xFFFFB703;
                    bgBase = 0xCC2A1B07;
                    textColor = 0xFFFFD166;
                } else if (item.stars() == 4) {
                    borderColor = 0xFFC77DFF;
                    bgBase = 0xCC200F2E;
                    textColor = 0xFFE0AAFF;
                } else {
                    borderColor = 0xFF4CC9F0;
                    bgBase = 0xCC0D1C2A;
                    textColor = 0xFFE0FBFC;
                }

                int bg = hovered ? 0xEE30264E : bgBase;
                int outline = hovered ? 0xFFFFFFFF : borderColor;
                guiGraphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, outline);
                guiGraphics.fill(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1, bg);

                int slotX = cardX + 2;
                int slotY = cardY + 2;
                int slotSize = 24;
                guiGraphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, borderColor);
                guiGraphics.fill(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, 0xCC080812);

                guiGraphics.renderItem(stack, slotX + 4, slotY + 4);
                guiGraphics.renderItemDecorations(font, stack, slotX + 4, slotY + 4);

                String name = (item.customName() != null && !item.customName().isEmpty())
                        ? item.customName()
                        : stack.getHoverName().getString();
                int maxNameW = cardW - 34;
                if (item.isRateUp()) maxNameW -= 20;
                if (font.width(name) > maxNameW) {
                    while (name.length() > 3 && font.width(name + "..") > maxNameW) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name += "..";
                }
                guiGraphics.drawString(font, name, cardX + 29, cardY + 4, textColor, true);

                String starStr = item.stars() + "★";
                guiGraphics.drawString(font, starStr, cardX + 29, cardY + 15, borderColor, true);

                if (item.isRateUp()) {
                    int upW = 18;
                    int upH = 9;
                    int upX = cardX + cardW - upW - 3;
                    int upY = cardY + 3;
                    guiGraphics.fill(upX, upY, upX + upW, upY + upH, 0xFFFFB703);
                    guiGraphics.drawString(font, "UP", upX + 3, upY + 1, 0xFF000000, false);
                }

                if (hovered) {
                    this.hoveredStack = stack;
                }
            }
        }

        // Scrollbar
        if (totalRows > visibleRows) {
            int scrollbarX = contentX + contentW - 4;
            int scrollbarY = contentY;
            int scrollbarH = contentH;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarH, 0x44000000);
            int thumbH = Math.max(12, (visibleRows * scrollbarH) / totalRows);
            int thumbY = scrollbarY + (poolScrollOffset * (scrollbarH - thumbH)) / maxScroll;
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, 0xFF8E7DBE);
        }
    }

    private void renderFilterPill(GuiGraphics guiGraphics, Font font, int x, int y, int w, int h, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = selected ? 0xFF4A3572 : (hovered ? 0xFF28243C : 0xFF181528);
        int border = selected ? 0xFFFFD166 : (hovered ? 0xFFAAAAAA : 0xFF352B4E);
        int text = selected ? 0xFFFFD166 : (hovered ? 0xFFFFFFFF : 0xFFB8B8D4);
        guiGraphics.fill(x, y, x + w, y + h, border);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        int textW = font.width(label);
        guiGraphics.drawString(font, label, x + (w - textW) / 2, y + (h - 8) / 2, text, true);
    }

    private void renderRatesAndRulesTab(GuiGraphics guiGraphics, Font font, int modalX, int modalY, int modalW, int modalH, int startY, int mouseX, int mouseY) {
        int x = modalX + 16;
        int y = startY;

        int boxW = modalW - 32;
        int boxH = modalH - (startY - modalY) - 30;
        guiGraphics.fill(x - 4, y - 4, x + boxW + 4, y + boxH, 0xAA121124);
        guiGraphics.fill(x - 3, y - 3, x + boxW + 3, y + boxH - 1, 0xEE161528);

        guiGraphics.drawString(font, "§6★ 5-STAR (Legendary): §f0.8% Base Rate", x, y + 2, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, "  §7- Soft Pity: Rolls 65-79 (rate increases sharply)", x, y + 14, 0xFFAAAAAA, true);
        guiGraphics.drawString(font, "  §7- Hard Pity: 80 Rolls Guaranteed", x, y + 26, 0xFFAAAAAA, true);

        y += 42;
        guiGraphics.drawString(font, "§5★ 4-STAR (Epic): §f6.0% Base Rate", x, y, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, "  §7- Hard Pity: 10 Rolls Guaranteed", x, y + 12, 0xFFAAAAAA, true);

        y += 28;
        guiGraphics.drawString(font, "§9★ 3-STAR (Supplies): §f93.2% Base Rate", x, y, 0xFFFFFFFF, true);

        y += 18;
        guiGraphics.fill(x, y, x + boxW, y + 1, 0xFF352B4E); // Divider
        y += 6;

        if ("FEATURED_RESONATOR".equalsIgnoreCase(banner.type())) {
            guiGraphics.drawString(font, "§e★ 50/50 Rule: §f50% Featured on 5★. If lost, next", x, y, 0xFFFFFFFF, true);
            guiGraphics.drawString(font, "  5★ is §6100% GUARANTEED§e featured item!", x, y + 12, 0xFFFFD166, true);
        } else if ("FEATURED_WEAPON".equalsIgnoreCase(banner.type())) {
            guiGraphics.drawString(font, "§e★ Weapon Rule: §a100% GUARANTEED featured weapon on 5★!", x, y, 0xFF55FF55, true);
        } else {
            guiGraphics.drawString(font, "§e★ Standard Pool: §fEvenly distributed standard items", x, y, 0xFFFFFFFF, true);
        }

        y += 28;
        int pity5 = ClientGachaData.getPityForType(banner.type());
        boolean guaranteed = ClientGachaData.isGuaranteed() && "FEATURED_RESONATOR".equalsIgnoreCase(banner.type());
        String status = "§7Your 5★ Pity: §e" + pity5 + " / 80" + (guaranteed ? " §6[Next 5★ Guaranteed]" : "");
        guiGraphics.drawString(font, status, x, y, 0xFFFFFFFF, true);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int modalW = Math.min(390, screenWidth - 24);
        int modalH = Math.min(250, screenHeight - 24);
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        // Click outside closes modal
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            parent.closeDetails();
            return true;
        }

        // Close button [X]
        int closeX = modalX + modalW - 18;
        int closeY = modalY + 4;
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14) {
            parent.closeDetails();
            return true;
        }

        // Bottom Close button
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Tabs Row
        int tabY = modalY + 25;
        int tabH = 18;
        int tabW = (modalW - 24) / 2;
        int tab0X = modalX + 10;
        int tab1X = tab0X + tabW + 4;

        if (mouseY >= tabY && mouseY <= tabY + tabH) {
            if (mouseX >= tab0X && mouseX <= tab0X + tabW) {
                this.activeTab = 0;
                this.poolScrollOffset = 0;
                return true;
            } else if (mouseX >= tab1X && mouseX <= tab1X + tabW) {
                this.activeTab = 1;
                return true;
            }
        }

        // Tab 0 Filter pills
        if (activeTab == 0) {
            int filterY = tabY + tabH + 4;
            int filterH = 15;
            if (mouseY >= filterY && mouseY <= filterY + filterH) {
                if (mouseX >= modalX + 10 && mouseX <= modalX + 46) {
                    this.poolFilter = 0;
                    this.poolScrollOffset = 0;
                    return true;
                } else if (mouseX >= modalX + 49 && mouseX <= modalX + 81) {
                    this.poolFilter = 5;
                    this.poolScrollOffset = 0;
                    return true;
                } else if (mouseX >= modalX + 84 && mouseX <= modalX + 116) {
                    this.poolFilter = 4;
                    this.poolScrollOffset = 0;
                    return true;
                } else if (mouseX >= modalX + 119 && mouseX <= modalX + 151) {
                    this.poolFilter = 3;
                    this.poolScrollOffset = 0;
                    return true;
                }
            }
        }

        return true; // Consume click so it does not trigger widgets behind modal
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == 0) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int modalW = Math.min(390, screenWidth - 24);
            int modalH = Math.min(250, screenHeight - 24);
            int modalX = (screenWidth - modalW) / 2;
            int modalY = (screenHeight - modalH) / 2;

            int contentX = modalX + 10;
            int contentY = modalY + 25 + 18 + 4 + 15 + 5;
            int contentW = modalW - 20;
            int contentH = modalY + modalH - 26 - contentY;

            if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
                int matchingCount = 0;
                for (SyncBannerDataPayload.BannerItemData item : banner.items()) {
                    if (poolFilter == 0 || poolFilter == item.stars()) matchingCount++;
                }
                int totalRows = (matchingCount + 1) / 2;
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
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeDetails();
            return true;
        }
        return true;
    }
}
