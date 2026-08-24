package com.holysweet.linggacha.client.screen;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.network.ClaimMailboxItemPayload;
import com.holysweet.linggacha.network.RequestMailboxPayload;
import com.holysweet.linggacha.network.SyncBannerDataPayload;
import com.holysweet.linggacha.network.SyncMailboxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GachaMailboxModal {

    private final GachaScreen parent;
    private final List<SyncMailboxPayload.MailboxItemData> items = new ArrayList<>();

    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 5;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    private Button claimAllBtn;
    private Button prevBtn;
    private Button nextBtn;
    private Button closeBtn;
    private final List<Button> claimItemButtons = new ArrayList<>();

    public GachaMailboxModal(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int screenWidth, int screenHeight) {
        int modalW = 380;
        int modalH = 240;
        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;

        this.claimAllBtn = Button.builder(Component.literal("§aClaim All"), b -> claimAll())
                .bounds(modalX + modalW - 95, modalY + 22, 85, 16).build();

        int bottomY = modalY + modalH - 24;
        this.prevBtn = Button.builder(Component.literal("< Prev"), b -> prevPage())
                .bounds(modalX + 12, bottomY, 65, 18).build();

        this.nextBtn = Button.builder(Component.literal("Next >"), b -> nextPage())
                .bounds(modalX + modalW - 77 - 80, bottomY, 65, 18).build();

        this.closeBtn = Button.builder(Component.literal("Close"), b -> parent.closeModal())
                .bounds(modalX + modalW - 72, bottomY, 60, 18).build();

        updateItemButtons(modalX, modalY);
        requestMailboxData();
    }

    public void updateData(SyncMailboxPayload payload) {
        this.items.clear();
        this.items.addAll(payload.items());
        int maxPage = Math.max(0, (items.size() - 1) / ITEMS_PER_PAGE);
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }
        ClientGachaData.setMailboxCount(items.size());
        if (Minecraft.getInstance().screen != null) {
            int modalW = 380;
            int modalH = 240;
            int modalX = (Minecraft.getInstance().screen.width - modalW) / 2;
            int modalY = (Minecraft.getInstance().screen.height - modalH) / 2;
            updateItemButtons(modalX, modalY);
        }
    }

    private void updateItemButtons(int modalX, int modalY) {
        claimItemButtons.clear();
        int startIdx = currentPage * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, items.size());
        int startY = modalY + 44;
        int rowH = 32;

        for (int i = startIdx; i < endIdx; i++) {
            final SyncMailboxPayload.MailboxItemData item = items.get(i);
            int rowY = startY + (i - startIdx) * rowH;
            Button claimBtn = Button.builder(Component.literal("§aClaim"), b -> claimSingle(item.mailId()))
                    .bounds(modalX + 380 - 68, rowY + 6, 56, 18).build();
            claimItemButtons.add(claimBtn);
        }
    }

    private void requestMailboxData() {
        PacketDistributor.sendToServer(new RequestMailboxPayload());
    }

    private void claimSingle(String mailId) {
        PacketDistributor.sendToServer(new ClaimMailboxItemPayload(mailId));
    }

    private void claimAll() {
        if (!items.isEmpty()) {
            PacketDistributor.sendToServer(new ClaimMailboxItemPayload("ALL"));
        }
    }

    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            int modalW = 380;
            int modalH = 240;
            int modalX = (Minecraft.getInstance().screen.width - modalW) / 2;
            int modalY = (Minecraft.getInstance().screen.height - modalH) / 2;
            updateItemButtons(modalX, modalY);
        }
    }

    private void nextPage() {
        int maxPage = Math.max(0, (items.size() - 1) / ITEMS_PER_PAGE);
        if (currentPage < maxPage) {
            currentPage++;
            int modalW = 380;
            int modalH = 240;
            int modalX = (Minecraft.getInstance().screen.width - modalW) / 2;
            int modalY = (Minecraft.getInstance().screen.height - modalH) / 2;
            updateItemButtons(modalX, modalY);
        }
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

        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0A0A16);
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF141424);

        // Header Title
        guiGraphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 20, 0xFF2A1C40);
        guiGraphics.drawString(Minecraft.getInstance().font, "GACHA REWARD MAILBOX", modalX + 10, modalY + 6, 0xFFFFD700, true);

        // Pending Count Info
        String pendingInfo = "Pending Items: §e" + items.size() + "§r";
        guiGraphics.drawString(Minecraft.getInstance().font, pendingInfo, modalX + 12, modalY + 26, 0xFFAAAAAA, true);

        if (claimAllBtn != null) {
            claimAllBtn.active = !items.isEmpty();
            claimAllBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // List Area
        int startY = modalY + 44;
        int rowH = 32;
        int startIdx = currentPage * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, items.size());

        ItemStack hoveredStack = null;

        if (items.isEmpty()) {
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, "§7Your mailbox is empty.", modalX + modalW / 2, modalY + 100, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, "§8All pull rewards have been claimed to inventory.", modalX + modalW / 2, modalY + 116, 0xFF666677);
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                SyncMailboxPayload.MailboxItemData item = items.get(i);
                int rowY = startY + (i - startIdx) * rowH;

                // Alternating row background
                int bg = (i % 2 == 0) ? 0xFF18182A : 0xFF1E1E34;
                guiGraphics.fill(modalX + 10, rowY, modalX + modalW - 10, rowY + rowH - 2, bg);

                // Star Rarity Accent Line
                int starColor = switch (item.stars()) {
                    case 5 -> 0xFFFFD700;
                    case 4 -> 0xFFB870FF;
                    default -> 0xFF4CC9F0;
                };
                guiGraphics.fill(modalX + 10, rowY, modalX + 13, rowY + rowH - 2, starColor);

                // Item Stack Render
                ItemStack stack = getItemStack(item);
                guiGraphics.renderItem(stack, modalX + 18, rowY + 6);
                guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, modalX + 18, rowY + 6);

                // Check Hover on Item for Tooltip
                if (mouseX >= modalX + 18 && mouseX <= modalX + 36 && mouseY >= rowY + 6 && mouseY <= rowY + 24) {
                    hoveredStack = stack;
                }

                // Item Name and Details
                String name = (item.customName() != null && !item.customName().isEmpty()) ? item.customName() : stack.getHoverName().getString();
                String starStr = item.stars() + "-Star";
                guiGraphics.drawString(Minecraft.getInstance().font, "§l" + name + " §r(" + starStr + ")", modalX + 42, rowY + 4, starColor, true);

                String dateStr = DATE_FORMAT.format(new Date(item.timestamp()));
                String bannerTitle = getBannerTitle(item.bannerId());
                guiGraphics.drawString(Minecraft.getInstance().font, "§8Pool: §7" + bannerTitle + " §8| " + dateStr, modalX + 42, rowY + 16, 0xFF888899, false);

                // Render Claim Button
                int btnIdx = i - startIdx;
                if (btnIdx >= 0 && btnIdx < claimItemButtons.size()) {
                    claimItemButtons.get(btnIdx).render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
        }

        // Pagination & Navigation
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        String pageInfo = "Page " + (currentPage + 1) + " / " + maxPage;
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, pageInfo, modalX + modalW / 2 - 20, modalY + modalH - 20, 0xFFAAAAAA);

        if (prevBtn != null) {
            prevBtn.active = currentPage > 0;
            prevBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (nextBtn != null) {
            nextBtn.active = (currentPage + 1) < maxPage;
            nextBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (closeBtn != null) {
            closeBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Tooltip render on top
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, hoveredStack, mouseX, mouseY);
        }

        guiGraphics.pose().popPose();
    }

    private String getBannerTitle(String bannerId) {
        if (bannerId == null) return "Standard";
        for (SyncBannerDataPayload.ClientBannerInfo b : ClientGachaData.getBanners()) {
            if (b.id().equalsIgnoreCase(bannerId)) {
                return b.title();
            }
        }
        return bannerId;
    }

    private ItemStack getItemStack(SyncMailboxPayload.MailboxItemData item) {
        ItemStack stack = ItemStack.EMPTY;
        if (item.snbt() != null && !item.snbt().isEmpty()) {
            try {
                var tag = TagParser.parseTag(item.snbt());
                if (Minecraft.getInstance().level != null) {
                    stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), tag);
                }
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
        return stack;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (claimAllBtn != null && claimAllBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (prevBtn != null && prevBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (nextBtn != null && nextBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (closeBtn != null && closeBtn.mouseClicked(mouseX, mouseY, button)) return true;

        for (Button btn : claimItemButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }

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
        return false;
    }
}
