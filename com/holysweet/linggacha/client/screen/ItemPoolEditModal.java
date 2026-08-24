package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.network.AdminRemoveGachaItemPayload;
import com.holysweet.linggacha.network.AdminRequestItemPoolPayload;
import com.holysweet.linggacha.network.AdminSyncItemPoolPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ItemPoolEditModal {

    private final GachaScreen parent;
    private final String bannerId;
    private final String bannerTitle;

    private final List<AdminSyncItemPoolPayload.ItemEntryData> items = new ArrayList<>();
    private int scrollOffset = 0;

    private Button addHandBtn;
    private Button closeBtn;

    public ItemPoolEditModal(GachaScreen parent, String bannerId, String bannerTitle) {
        this.parent = parent;
        this.bannerId = bannerId;
        this.bannerTitle = bannerTitle;
    }

    public void init(int screenWidth, int screenHeight) {
        int modalW = 340;
        int modalH = 220;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        int btnY = modalY + modalH - 24;

        this.addHandBtn = Button.builder(Component.literal("§a+ Add Hand Item"), b -> addFromHand())
                .bounds(modalX + 15, btnY, 145, 18).build();

        this.closeBtn = Button.builder(Component.literal("Close"), b -> parent.closeModal())
                .bounds(modalX + 180, btnY, 145, 18).build();

        // Request latest items from server
        PacketDistributor.sendToServer(new AdminRequestItemPoolPayload(bannerId));
    }

    public void updateItems(List<AdminSyncItemPoolPayload.ItemEntryData> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
    }

    private void addFromHand() {
        if (Minecraft.getInstance().player != null) {
            ItemStack held = Minecraft.getInstance().player.getMainHandItem();
            if (held.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§c[Gacha Editor] Please hold an item in your main hand first!"), true);
                return;
            }
            parent.openItemAddModal(bannerId, held);
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        int modalW = 340;
        int modalH = 220;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // Full Screen Dark Dim Backdrop
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

        // Dark Modal Background
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0A0A16);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Header Title
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 22, 0xFF2A1C40);
        String header = "POOL MANAGER: " + bannerTitle + " (" + items.size() + " items)";
        guiGraphics.drawString(Minecraft.getInstance().font, header, modalX + 10, modalY + 7, 0xFFFFD700, true);

        // Item List Box
        int listX = modalX + 12;
        int listY = modalY + 26;
        int listW = modalW - 24;
        int listH = modalH - 56;
        int itemH = 22;
        int visibleCount = listH / itemH;

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0D0D18);
        guiGraphics.fill(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1, 0xFF18182A);

        int maxScroll = Math.max(0, items.size() - visibleCount);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < visibleCount && (i + scrollOffset) < items.size(); i++) {
            int idx = i + scrollOffset;
            AdminSyncItemPoolPayload.ItemEntryData item = items.get(idx);
            int rowY = listY + 2 + i * itemH;

            boolean rowHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + itemH - 2;
            int rowBg = rowHovered ? 0xFF2A2A44 : (i % 2 == 0 ? 0xFF1E1E32 : 0xFF18182A);
            guiGraphics.fill(listX + 2, rowY, listX + listW - 2, rowY + itemH - 2, rowBg);

            // Item Icon
            ItemStack stack = getItemStack(item.itemId(), item.count());
            guiGraphics.renderItem(stack, listX + 4, rowY + 2);

            // Stars & Rate-Up
            String stars = getStarsString(item.stars());
            int starColor = item.stars() >= 5 ? 0xFFFFB703 : (item.stars() == 4 ? 0xFFC77DFF : 0xFF4CC9F0);
            guiGraphics.drawString(Minecraft.getInstance().font, stars, listX + 24, rowY + 6, starColor, true);

            // Item Name & Qty
            String name = (item.customName() != null && !item.customName().isEmpty()) ? item.customName() : stack.getHoverName().getString();
            if (item.count() > 1) name = item.count() + "x " + name;
            if (item.isRateUp()) name += " §e[Rate-Up]";
            if (Minecraft.getInstance().font.width(name) > 130) {
                name = name.substring(0, Math.min(name.length(), 16)) + "..";
            }
            guiGraphics.drawString(Minecraft.getInstance().font, name, listX + 65, rowY + 6, 0xFFFFFFFF, true);

            // Weight
            String weightStr = "W: " + item.weight();
            guiGraphics.drawString(Minecraft.getInstance().font, weightStr, listX + 205, rowY + 6, 0xFFAAAAAA, true);

            // Edit button [Edit]
            int editX = listX + listW - 60;
            boolean editHovered = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + itemH - 4;
            guiGraphics.fill(editX, rowY + 2, editX + 32, rowY + itemH - 4, editHovered ? 0xFF3D5A80 : 0xFF293241);
            guiGraphics.drawString(Minecraft.getInstance().font, "Edit", editX + 6, rowY + 5, 0xFFE0FBFC, true);

            // Delete button [X]
            int delX = listX + listW - 24;
            boolean delHovered = mouseX >= delX && mouseX <= delX + 20 && mouseY >= rowY + 2 && mouseY <= rowY + itemH - 4;
            guiGraphics.fill(delX, rowY + 2, delX + 20, rowY + itemH - 4, delHovered ? 0xFFE63946 : 0xFF7A1C24);
            guiGraphics.drawString(Minecraft.getInstance().font, "X", delX + 7, rowY + 5, 0xFFFFFFFF, true);
        }

        if (addHandBtn != null) addHandBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (closeBtn != null) closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int modalW = 340;
        int modalH = 220;
        int modalX = (parent.width - modalW) / 2;
        int modalY = (parent.height - modalH) / 2;

        int listX = modalX + 12;
        int listY = modalY + 26;
        int listW = modalW - 24;
        int listH = modalH - 56;
        int itemH = 22;
        int visibleCount = listH / itemH;

        for (int i = 0; i < visibleCount && (i + scrollOffset) < items.size(); i++) {
            int idx = i + scrollOffset;
            AdminSyncItemPoolPayload.ItemEntryData item = items.get(idx);
            int rowY = listY + 2 + i * itemH;

            // Check Delete Button
            int delX = listX + listW - 24;
            if (mouseX >= delX && mouseX <= delX + 20 && mouseY >= rowY + 2 && mouseY <= rowY + itemH - 4) {
                PacketDistributor.sendToServer(new AdminRemoveGachaItemPayload(bannerId, item.index()));
                return true;
            }

            // Check Edit Button or Clicking on the row
            int editX = listX + listW - 60;
            boolean clickedEditBtn = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + itemH - 4;
            boolean clickedRow = mouseX >= listX && mouseX < delX && mouseY >= rowY && mouseY <= rowY + itemH - 2;
            if (clickedEditBtn || clickedRow) {
                parent.openItemEditModal(bannerId, item);
                return true;
            }
        }

        if (addHandBtn != null && addHandBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) return true;

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listH = 220 - 56;
        int itemH = 22;
        int visibleCount = listH / itemH;
        int maxScroll = Math.max(0, items.size() - visibleCount);
        if (maxScroll > 0) {
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                return true;
            } else if (scrollY < 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeModal();
            return true;
        }
        return false;
    }

    private String getStarsString(int stars) {
        if (stars >= 5) return "5-Star";
        if (stars == 4) return "4-Star";
        return "3-Star";
    }

    private ItemStack getItemStack(String itemId, int count) {
        try {
            var opt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId));
            if (opt.isPresent()) return new ItemStack(opt.get(), Math.max(1, count));
        } catch (Exception ignored) {}
        return new ItemStack(Items.DIRT, 1);
    }
}
