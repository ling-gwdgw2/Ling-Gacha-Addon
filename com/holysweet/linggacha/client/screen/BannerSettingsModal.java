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
    private EditBox bgBox;
    private EditBox focusedEditBox = null;

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

    public void init(int screenWidth, int screenHeight) {
        int modalW = 290;
        int modalH = 230;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        int inputX = modalX + 80;
        int inputW = 195;
        int y = modalY + 24;

        this.idBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Banner ID"));
        this.idBox.setMaxLength(64);
        this.idBox.setValue(banner.id() != null ? banner.id() : "");
        this.idBox.setEditable(isNewBanner);
        y += 17;

        this.titleBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Title"));
        this.titleBox.setMaxLength(128);
        this.titleBox.setValue(banner.title() != null ? banner.title() : "");
        y += 17;

        this.subtitleBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Subtitle"));
        this.subtitleBox.setMaxLength(256);
        this.subtitleBox.setValue(banner.subtitle() != null ? banner.subtitle() : "");
        y += 17;

        this.costBox = new EditBox(Minecraft.getInstance().font, inputX, y, 60, 14, Component.literal("Cost"));
        this.costBox.setMaxLength(10);
        this.costBox.setValue(String.valueOf(banner.cost()));

        this.discountBox = new EditBox(Minecraft.getInstance().font, inputX + 115, y, 40, 14, Component.literal("Discount"));
        this.discountBox.setMaxLength(3);
        this.discountBox.setValue(String.valueOf(banner.discount()));
        y += 17;

        this.previewBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Preview Item"));
        this.previewBox.setMaxLength(128);
        this.previewBox.setValue(banner.previewItem() != null ? banner.previewItem() : "minecraft:netherite_sword");
        y += 17;

        this.bgBox = new EditBox(Minecraft.getInstance().font, inputX, y, inputW, 14, Component.literal("Background Texture"));
        this.bgBox.setMaxLength(256);
        this.bgBox.setValue(banner.backgroundImage() != null ? banner.backgroundImage() : "");
        y += 19;

        this.typeBtn = Button.builder(Component.literal("Type: " + selectedType.getDisplayName()), b -> cycleType())
                .bounds(inputX, y, inputW, 16).build();
        y += 22;

        this.saveBtn = Button.builder(Component.literal("§aSave Banner"), b -> save())
                .bounds(modalX + 15, y, 80, 18).build();

        this.deleteBtn = Button.builder(Component.literal("§cDelete"), b -> delete())
                .bounds(modalX + 105, y, 75, 18).build();
        if (isNewBanner) deleteBtn.active = false;

        this.closeBtn = Button.builder(Component.literal("Cancel"), b -> parent.closeModal())
                .bounds(modalX + 190, y, 80, 18).build();

        setFocusedEditBox(isNewBanner ? idBox : titleBox);
    }

    private boolean isMouseOver(EditBox box, double mouseX, double mouseY) {
        return box != null && mouseX >= box.getX() && mouseX <= (box.getX() + box.getWidth()) && mouseY >= box.getY() && mouseY <= (box.getY() + box.getHeight());
    }

    private void setFocusedEditBox(EditBox target) {
        if (idBox != null) idBox.setFocused(idBox == target);
        if (titleBox != null) titleBox.setFocused(titleBox == target);
        if (subtitleBox != null) subtitleBox.setFocused(subtitleBox == target);
        if (costBox != null) costBox.setFocused(costBox == target);
        if (discountBox != null) discountBox.setFocused(discountBox == target);
        if (previewBox != null) previewBox.setFocused(previewBox == target);
        if (bgBox != null) bgBox.setFocused(bgBox == target);
        this.focusedEditBox = target;
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
        String background = bgBox.getValue().trim();
        if (background.isEmpty()) background = null;

        PacketDistributor.sendToServer(new AdminUpdateBannerPayload(
                id, title, subtitle, selectedType.name(), cost, discount, preview, background
        ));
        parent.closeModal();
    }

    private void delete() {
        if (!isNewBanner) {
            PacketDistributor.sendToServer(new AdminDeleteBannerPayload(banner.id()));
            parent.closeModal();
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        int modalW = 290;
        int modalH = 230;
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
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 20, 0xFF2A1C40);
        String header = isNewBanner ? "CREATE NEW BANNER" : "EDIT BANNER SETTINGS";
        guiGraphics.drawString(Minecraft.getInstance().font, header, modalX + 10, modalY + 6, 0xFFFFD700, true);

        // Field Labels
        int y = modalY + 26;
        guiGraphics.drawString(Minecraft.getInstance().font, "Banner ID:", modalX + 10, y, 0xFFAAAAAA, true);
        y += 17;
        guiGraphics.drawString(Minecraft.getInstance().font, "Title:", modalX + 10, y, 0xFFAAAAAA, true);
        y += 17;
        guiGraphics.drawString(Minecraft.getInstance().font, "Subtitle:", modalX + 10, y, 0xFFAAAAAA, true);
        y += 17;
        guiGraphics.drawString(Minecraft.getInstance().font, "Cost (G):", modalX + 10, y, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "Disc %:", modalX + 148, y, 0xFFAAAAAA, true);
        y += 17;
        guiGraphics.drawString(Minecraft.getInstance().font, "Preview Item:", modalX + 10, y, 0xFFAAAAAA, true);
        y += 17;
        guiGraphics.drawString(Minecraft.getInstance().font, "Background:", modalX + 10, y, 0xFFAAAAAA, true);
        y += 19;
        guiGraphics.drawString(Minecraft.getInstance().font, "Pool Type:", modalX + 10, y, 0xFFAAAAAA, true);

        // Render EditBoxes & Buttons
        if (idBox != null) idBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (titleBox != null) titleBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (subtitleBox != null) subtitleBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (costBox != null) costBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (discountBox != null) discountBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (previewBox != null) previewBox.render(guiGraphics, mouseX, mouseY, partialTick);
        if (bgBox != null) bgBox.render(guiGraphics, mouseX, mouseY, partialTick);

        if (typeBtn != null) typeBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (saveBtn != null) saveBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (deleteBtn != null) deleteBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        if (closeBtn != null) closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EditBox clickedBox = null;
        if (idBox != null && isMouseOver(idBox, mouseX, mouseY)) clickedBox = idBox;
        else if (titleBox != null && isMouseOver(titleBox, mouseX, mouseY)) clickedBox = titleBox;
        else if (subtitleBox != null && isMouseOver(subtitleBox, mouseX, mouseY)) clickedBox = subtitleBox;
        else if (costBox != null && isMouseOver(costBox, mouseX, mouseY)) clickedBox = costBox;
        else if (discountBox != null && isMouseOver(discountBox, mouseX, mouseY)) clickedBox = discountBox;
        else if (previewBox != null && isMouseOver(previewBox, mouseX, mouseY)) clickedBox = previewBox;
        else if (bgBox != null && isMouseOver(bgBox, mouseX, mouseY)) clickedBox = bgBox;

        if (clickedBox != null) {
            setFocusedEditBox(clickedBox);
            clickedBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }

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
        if (focusedEditBox != null) {
            if (focusedEditBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedEditBox != null) {
            if (focusedEditBox.charTyped(codePoint, modifiers)) return true;
        }
        return false;
    }
}
