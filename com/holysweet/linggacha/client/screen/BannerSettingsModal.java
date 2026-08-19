package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.gacha.GachaBanner;
import com.holysweet.linggacha.network.AdminDeleteBannerPayload;
import com.holysweet.linggacha.network.AdminUpdateBannerPayload;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class BannerSettingsModal {

    private final GachaScreen parent;
    private final SyncBannerDataPayload.ClientBannerInfo banner;
    private final boolean isNewBanner;

    private EditBox idBox;
    private EditBox titleBox;
    private EditBox subtitleBox;
    private EditBox costBox;
    private EditBox discountBox;
    private EditBox previewBox;

    private GachaBanner.BannerType selectedType;
    private Button typeBtn;
    private Button saveBtn;
    private Button deleteBtn;
    private Button closeBtn;

    public BannerSettingsModal(GachaScreen parent, SyncBannerDataPayload.ClientBannerInfo banner, boolean isNewBanner) {
        this.parent = parent;
        this.banner = banner;
        this.isNewBanner = isNewBanner;
        try {
            this.selectedType = GachaBanner.BannerType.valueOf(banner.type());
        } catch (Exception e) {
            this.selectedType = GachaBanner.BannerType.FEATURED_RESONATOR;
        }
    }

    public void init(int leftPos, int topPos) {
        int modalW = 280;
        int modalH = 220;
        int modalX = leftPos + (360 - modalW) / 2;
        int modalY = topPos + (230 - modalH) / 2;

        int inputX = modalX + 75;
        int inputW = 190;
        int y = modalY + 26;

        this.idBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Banner ID"));
        this.idBox.setValue(banner.id());
        this.idBox.setEditable(isNewBanner);
        y += 18;

        this.titleBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Title"));
        this.titleBox.setValue(banner.title());
        y += 18;

        this.subtitleBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Subtitle"));
        this.subtitleBox.setValue(banner.subtitle());
        y += 18;

        this.costBox = new EditBox(Minecraft.getInstance().font, inputX, y, 60, 14, Component.literal("Cost"));
        this.costBox.setValue(String.valueOf(banner.cost()));

        this.discountBox = new EditBox(Minecraft.getInstance().font, inputX + 110, y, 40, 14, Component.literal("Discount"));
        this.discountBox.setValue(String.valueOf(banner.discount()));
        y += 18;

        this.previewBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Preview Item"));
        this.previewBox.setValue(banner.previewItem());
        y += 20;

        this.typeBtn = Button.builder(Component.literal("Type: " + selectedType.getDisplayName()), b -> cycleType())
                .bounds(inputX, y, inputW, 16).build();
        y += 24;

        this.saveBtn = Button.builder(Component.literal("§aSave Banner"), b -> save())
                .bounds(modalX + 15, y, 80, 18).build();

        this.deleteBtn = Button.builder(Component.literal("§cDelete"), b -> delete())
                .bounds(modalX + 105, y, 75, 18).build();
        if (isNewBanner) deleteBtn.active = false;

        this.closeBtn = Button.builder(Component.literal("Cancel"), b -> parent.closeModal())
                .bounds(modalX + 190, y, 75, 18).build();
    }

    private void cycleType() {
        GachaBanner.BannerType[] types = GachaBanner.BannerType.values();
        int next = (selectedType.ordinal() + 1) % types.length;
        selectedType = types[next];
        if (typeBtn != null) {
            typeBtn.setMessage(Component.literal("Type: " + selectedType.getDisplayName()));
        }
    }

    private void save() {
        String id = idBox.getValue().trim();
        if (id.isEmpty()) return;
        String title = titleBox.getValue().trim();
        String subtitle = subtitleBox.getValue().trim();
        int cost = 160;
        try { cost = Math.max(1, Integer.parseInt(costBox.getValue().trim())); } catch (Exception ignored) {}
        int discount = 0;
        try { discount = Math.max(0, Math.min(100, Integer.parseInt(discountBox.getValue().trim()))); } catch (Exception ignored) {}
        String preview = previewBox.getValue().trim();
        if (preview.isEmpty()) preview = "minecraft:netherite_sword";

        PacketDistributor.sendToServer(new AdminUpdateBannerPayload(
                id, title, subtitle, selectedType.name(), cost, discount, preview
        ));
        parent.closeModal();
    }

    private void delete() {
        if (!isNewBanner) {
            PacketDistributor.sendToServer(new AdminDeleteBannerPayload(banner.id()));
            parent.closeModal();
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos) {
        int modalW = 280;
        int modalH = 220;
        int modalX = leftPos + (360 - modalW) / 2;
        int modalY = topPos + (230 - modalH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // Dark Modal Background
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0A0A16);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Header Title
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 20, 0xFF2A1C40);
        String header = isNewBanner ? "✦ CREATE NEW BANNER ✦" : "✦ EDIT BANNER SETTINGS ✦";
        guiGraphics.drawString(Minecraft.getInstance().font, header, modalX + 10, modalY + 6, 0xFFFFD700, true);

        // Field Labels
        int y = modalY + 28;
        guiGraphics.drawString(Minecraft.getInstance().font, "Banner ID:", modalX + 12, y, 0xFFAAAAAA, true);
        y += 18;
        guiGraphics.drawString(Minecraft.getInstance().font, "Title:", modalX + 12, y, 0xFFAAAAAA, true);
        y += 18;
        guiGraphics.drawString(Minecraft.getInstance().font, "Subtitle:", modalX + 12, y, 0xFFAAAAAA, true);
        y += 18;
        guiGraphics.drawString(Minecraft.getInstance().font, "Cost (G):", modalX + 12, y, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Disc %:", modalX + 145, y, 0xFFAAAAAA, true);
        y += 18;
        guiGraphics.drawString(Minecraft.getInstance().font, "Preview Item:", modalX + 12, y, 0xFFAAAAAA, true);
        y += 20;
        guiGraphics.drawString(Minecraft.getInstance().font, "Pool Type:", modalX + 12, y, 0xFFAAAAAA, true);

        // Render EditBoxes & Buttons
        if (idBox != null) idBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (titleBox != null) titleBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (subtitleBox != null) subtitleBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (costBox != null) costBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (discountBox != null) discountBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (previewBox != null) previewBox.render(guiGraphics, mouseX, mouseY, partialTick);

        if (typeBtn != null) typeBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (saveBtn != null) saveBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (deleteBtn != null) deleteBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (closeBtn != null) closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (idBox != null && idBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (titleBox != null && titleBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (subtitleBox != null && subtitleBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (costBox != null && costBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (discountBox != null && discountBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (previewBox != null && previewBox.mouseClicked(mouseX, mouseY, button)) return true;

        if (typeBtn != null && typeBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (saveBtn != null && saveBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (deleteBtn != null && deleteBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) return true;

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeModal();
            return true;
        }
        if (idBox != null && idBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (titleBox != null && titleBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (subtitleBox != null && subtitleBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (costBox != null && costBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (discountBox != null && discountBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (previewBox != null && previewBox.keyPressed(keyCode, scanCode, modifiers)) return true;

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (idBox != null && idBox.charTyped(codePoint, modifiers)) return true;
        if (titleBox != null && titleBox.charTyped(codePoint, modifiers)) return true;
        if (subtitleBox != null && subtitleBox.charTyped(codePoint, modifiers)) return true;
        if (costBox != null && costBox.charTyped(codePoint, modifiers)) return true;
        if (discountBox != null && discountBox.charTyped(codePoint, modifiers)) return true;
        if (previewBox != null && previewBox.charTyped(codePoint, modifiers)) return true;

        return false;
    }
}
