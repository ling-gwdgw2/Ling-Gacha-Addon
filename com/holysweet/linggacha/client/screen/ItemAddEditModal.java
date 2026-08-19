package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.gacha.GachaRarity;
import com.holysweet.linggacha.network.AdminAddGachaItemPayload;
import com.holysweet.linggacha.network.AdminSyncItemPoolPayload;
import com.holysweet.linggacha.network.AdminUpdateGachaItemPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class ItemAddEditModal {

    private final GachaScreen parent;
    private final String bannerId;
    private final int itemIndex; // -1 for new item
    private final String initialItemId;
    private final ItemStack previewStack;
    private final String initialSnbt;

    private EditBox nameBox;
    private EditBox countBox;
    private EditBox weightBox;

    private GachaRarity selectedRarity;
    private boolean isRateUp;

    private Button rarity3Btn;
    private Button rarity4Btn;
    private Button rarity5Btn;
    private Button rateUpBtn;
    private Button saveBtn;
    private Button cancelBtn;

    // Constructor for adding item from held ItemStack
    public ItemAddEditModal(GachaScreen parent, String bannerId, ItemStack heldStack) {
        this.parent = parent;
        this.bannerId = bannerId;
        this.itemIndex = -1;
        this.initialItemId = BuiltInRegistries.ITEM.getKey(heldStack.getItem()).toString();
        this.previewStack = heldStack.copy();
        this.initialSnbt = null;
        this.selectedRarity = GachaRarity.FOUR_STAR;
        this.isRateUp = false;
    }

    // Constructor for editing existing item
    public ItemAddEditModal(GachaScreen parent, String bannerId, AdminSyncItemPoolPayload.ItemEntryData data) {
        this.parent = parent;
        this.bannerId = bannerId;
        this.itemIndex = data.index();
        this.initialItemId = data.itemId();
        this.previewStack = getStack(data.itemId(), data.count());
        this.initialSnbt = data.snbt();
        this.selectedRarity = GachaRarity.fromStars(data.stars());
        this.isRateUp = data.isRateUp();
    }

    private ItemStack getStack(String id, int count) {
        try {
            var opt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
            if (opt.isPresent()) return new ItemStack(opt.get(), count);
        } catch (Exception ignored) {}
        return new ItemStack(Items.IRON_INGOT, 1);
    }

    public void init(int leftPos, int topPos) {
        int modalW = 260;
        int modalH = 200;
        int modalX = leftPos + (360 - modalW) / 2;
        int modalY = topPos + (230 - modalH) / 2;

        int inputX = modalX + 75;
        int inputW = 170;
        int y = modalY + 30;

        this.nameBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Custom Name"));
        String defaultName = previewStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                ? previewStack.getHoverName().getString() : "";
        this.nameBox.setValue(defaultName);
        y += 20;

        this.countBox = new EditBox(Minecraft.getInstance().font, inputX, y, 50, 14, Component.literal("Count"));
        this.countBox.setValue(String.valueOf(Math.max(1, previewStack.getCount())));

        this.weightBox = new EditBox(Minecraft.getInstance().font, inputX + 110, y, 50, 14, Component.literal("Weight"));
        this.weightBox.setValue(selectedRarity == GachaRarity.FIVE_STAR ? "10" : (selectedRarity == GachaRarity.FOUR_STAR ? "20" : "50"));
        y += 22;

        // Rarity Star Buttons
        int rBtnW = 50;
        this.rarity3Btn = Button.builder(Component.literal("§93★ Blue"), b -> setRarity(GachaRarity.THREE_STAR))
                .bounds(modalX + 75, y, rBtnW, 16).build();
        this.rarity4Btn = Button.builder(Component.literal("§54★ Purple"), b -> setRarity(GachaRarity.FOUR_STAR))
                .bounds(modalX + 130, y, rBtnW, 16).build();
        this.rarity5Btn = Button.builder(Component.literal("§65★ Gold"), b -> setRarity(GachaRarity.FIVE_STAR))
                .bounds(modalX + 185, y, rBtnW, 16).build();
        y += 22;

        this.rateUpBtn = Button.builder(Component.literal("Rate-Up: " + (isRateUp ? "§aYES" : "§cNO")), b -> toggleRateUp())
                .bounds(inputX, y, inputW, 16).build();
        y += 24;

        this.saveBtn = Button.builder(Component.literal("§aSave Item"), b -> save())
                .bounds(modalX + 25, y, 100, 18).build();

        this.cancelBtn = Button.builder(Component.literal("Cancel"), b -> parent.closeModal())
                .bounds(modalX + 135, y, 100, 18).build();
    }

    private void setRarity(GachaRarity r) {
        this.selectedRarity = r;
        if (weightBox != null) {
            weightBox.setValue(r == GachaRarity.FIVE_STAR ? "10" : (r == GachaRarity.FOUR_STAR ? "20" : "50"));
        }
    }

    private void toggleRateUp() {
        this.isRateUp = !isRateUp;
        if (rateUpBtn != null) {
            rateUpBtn.setMessage(Component.literal("Rate-Up: " + (isRateUp ? "§aYES" : "§cNO")));
        }
    }

    private void save() {
        String customName = nameBox.getValue().trim();
        int count = 1;
        try { count = Math.max(1, Integer.parseInt(countBox.getValue().trim())); } catch (Exception ignored) {}
        int weight = 10;
        try { weight = Math.max(1, Integer.parseInt(weightBox.getValue().trim())); } catch (Exception ignored) {}

        if (itemIndex < 0) {
            // Add new item
            PacketDistributor.sendToServer(new AdminAddGachaItemPayload(
                    bannerId, initialItemId, count, selectedRarity.getStars(),
                    customName.isEmpty() ? null : customName, weight, isRateUp, initialSnbt
            ));
        } else {
            // Update existing item
            PacketDistributor.sendToServer(new AdminUpdateGachaItemPayload(
                    bannerId, itemIndex, initialItemId, count, selectedRarity.getStars(),
                    customName.isEmpty() ? null : customName, weight, isRateUp, initialSnbt
            ));
        }
        parent.closeModal();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos) {
        int modalW = 260;
        int modalH = 200;
        int modalX = leftPos + (360 - modalW) / 2;
        int modalY = topPos + (230 - modalH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // Dark Modal Background
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0A0A16);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Header Title
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 20, 0xFF2A1C40);
        String header = (itemIndex < 0) ? "✦ ADD ITEM TO POOL ✦" : "✦ EDIT ITEM ✦";
        guiGraphics.drawString(Minecraft.getInstance().font, header, modalX + 10, modalY + 6, 0xFFFFD700, true);

        // Item Preview Box
        int previewX = modalX + 14;
        int previewY = modalY + 30;
        guiGraphics.fill(previewX, previewY, previewX + 44, previewY + 44, 0xFF221A38);
        guiGraphics.fill(previewX + 1, previewY + 1, previewX + 43, previewY + 43, 0xFF141022);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(previewX + 6, previewY + 6, 0);
        guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
        guiGraphics.renderItem(previewStack, 0, 0);
        guiGraphics.pose().popPose();

        // Labels
        int y = modalY + 32;
        guiGraphics.drawString(Minecraft.getInstance().font, "Name:", modalX + 62, y, 0xFFAAAAAA, true);
        y += 20;
        guiGraphics.drawString(Minecraft.getInstance().font, "Qty:", modalX + 62, y, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Weight:", modalX + 140, y, 0xFFAAAAAA, true);
        y += 24;
        guiGraphics.drawString(Minecraft.getInstance().font, "Stars:", modalX + 14, y, 0xFFAAAAAA, true);
        y += 22;
        guiGraphics.drawString(Minecraft.getInstance().font, "Rate-Up:", modalX + 14, y, 0xFFAAAAAA, true);

        // Render EditBoxes & Buttons
        if (nameBox != null) nameBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (countBox != null) countBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (weightBox != null) weightBox.render(guiGraphics, mouseX, mouseY, partialTick);

        if (rarity3Btn != null) rarity3Btn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (rarity4Btn != null) rarity4Btn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (rarity5Btn != null) rarity5Btn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (rateUpBtn != null) rateUpBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (saveBtn != null) saveBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (cancelBtn != null) cancelBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (nameBox != null && nameBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (countBox != null && countBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (weightBox != null && weightBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (rarity3Btn != null && rarity3Btn.mouseClicked(mouseX, mouseY, button)) return true;
        if (rarity4Btn != null && rarity4Btn.mouseClicked(mouseX, mouseY, button)) return true;
        if (rarity5Btn != null && rarity5Btn.mouseClicked(mouseX, mouseY, button)) return true;
        if (rateUpBtn != null && rateUpBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (saveBtn != null && saveBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (cancelBtn != null && cancelBtn.mouseClicked(mouseX, mouseY, button)) return true;

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeModal();
            return true;
        }
        if (nameBox != null && nameBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (countBox != null && countBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (weightBox != null && weightBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox != null && nameBox.charTyped(codePoint, modifiers)) return true;
        if (countBox != null && countBox.charTyped(codePoint, modifiers)) return true;
        if (weightBox != null && weightBox.charTyped(codePoint, modifiers)) return true;
        return false;
    }
}
