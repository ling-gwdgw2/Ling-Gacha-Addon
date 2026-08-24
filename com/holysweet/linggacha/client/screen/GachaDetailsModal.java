package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.network.SyncBannerDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GachaDetailsModal {

    private final GachaScreen parent;
    private final SyncBannerDataPayload.ClientBannerInfo banner;

    private Button closeBtn;

    public GachaDetailsModal(GachaScreen parent, SyncBannerDataPayload.ClientBannerInfo banner) {
        this.parent = parent;
        this.banner = banner;
    }

    public void init(int screenWidth, int screenHeight) {
        int modalW = 300;
        int modalH = 200;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        this.closeBtn = Button.builder(Component.literal("Close"), b -> parent.closeDetails())
                .bounds(modalX + modalW / 2 - 40, modalY + modalH - 25, 80, 18).build();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int screenWidth, int screenHeight) {
        int modalW = 300;
        int modalH = 200;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400.0F);

        // Full Screen Dark Dim Backdrop
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xAA000000);

        // Solid Dark Sci-Fi Modal Box
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF080812);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Title Bar
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 22, 0xFF221C38);
        String title = "CONVENE DETAILS // " + banner.title();
        guiGraphics.drawString(Minecraft.getInstance().font, title, modalX + 10, modalY + 7, 0xFFFFD700, true);

        // Drop Rates Section
        int y = modalY + 30;
        guiGraphics.drawString(Minecraft.getInstance().font, "§6★ 5-STAR (Legendary): §f0.8% Base Rate", modalX + 12, y, 0xFFFFFFFF, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "  §7- Soft Pity: Rolls 65-79 (rate increases sharply)", modalX + 12, y + 12, 0xFFAAAAAA, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "  §7- Hard Pity: 80 Rolls Guaranteed", modalX + 12, y + 24, 0xFFAAAAAA, true);

        y += 42;
        guiGraphics.drawString(Minecraft.getInstance().font, "§5★ 4-STAR (Epic): §f6.0% Base Rate", modalX + 12, y, 0xFFFFFFFF, true);
        guiGraphics.drawString(Minecraft.getInstance().font, "  §7- Hard Pity: 10 Rolls Guaranteed", modalX + 12, y + 12, 0xFFAAAAAA, true);

        y += 30;
        guiGraphics.drawString(Minecraft.getInstance().font, "§9★ 3-STAR (Supplies): §f93.2% Base Rate", modalX + 12, y, 0xFFFFFFFF, true);

        y += 18;
        // 50/50 Explanation
        if (banner.type().equals("FEATURED_RESONATOR")) {
            guiGraphics.drawString(Minecraft.getInstance().font, "§e★ 50/50 Rule: §f50% Featured on 5★. If lost, next", modalX + 12, y, 0xFFFFFFFF, true);
            guiGraphics.drawString(Minecraft.getInstance().font, "  5★ is 100% GUARANTEED featured item!", modalX + 12, y + 12, 0xFFFFD166, true);
        } else if (banner.type().equals("FEATURED_WEAPON")) {
            guiGraphics.drawString(Minecraft.getInstance().font, "§e★ Weapon Rule: §a100% GUARANTEED rate-up on 5★!", modalX + 12, y, 0xFF55FF55, true);
        } else {
            guiGraphics.drawString(Minecraft.getInstance().font, "§e★ Standard Pool: §fEvenly distributed standard items", modalX + 12, y, 0xFFFFFFFF, true);
        }

        if (closeBtn != null) closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            parent.closeDetails();
            return true;
        }
        return true;
    }
}
