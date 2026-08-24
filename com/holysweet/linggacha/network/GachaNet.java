package com.holysweet.linggacha.network;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.client.ClientHooks;
import com.holysweet.linggacha.gacha.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

public class GachaNet {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("ling_gacha").versioned("1.0.0");

        // 1. Request Open Gacha (Client -> Server)
        registrar.playToServer(
                RequestOpenGachaPayload.TYPE,
                RequestOpenGachaPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        openGachaForPlayer(player);
                    }
                })
        );

        // 2. Pull Convene Request (Client -> Server)
        registrar.playToServer(
                PullConvenePayload.TYPE,
                PullConvenePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        List<GachaItemEntry> results = GachaManager.INSTANCE.performConvene(player, payload.bannerId(), payload.pullCount());
                        if (!results.isEmpty()) {
                            int highestStars = results.stream().mapToInt(r -> r.getRarity().getStars()).max().orElse(3);
                            PlayerGachaData data = GachaManager.INSTANCE.getPlayerData(player.getUUID());
                            int pity5 = data.getPity5Star(payload.bannerId());

                            List<ConveneResultPayload.PrizeData> prizeList = new ArrayList<>();
                            for (GachaItemEntry item : results) {
                                prizeList.add(new ConveneResultPayload.PrizeData(item.getItemId(), item.getCount(), item.getRarity().getStars(), item.getCustomName()));
                            }

                            PacketDistributor.sendToPlayer(player, new ConveneResultPayload(payload.bannerId(), prizeList, highestStars, pity5, data.getAfterglowCorals()));
                        }
                    }
                })
        );

        // 3. Admin Update/Add Banner (Client -> Server)
        registrar.playToServer(
                AdminUpdateBannerPayload.TYPE,
                AdminUpdateBannerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            GachaBanner.BannerType type = GachaBanner.BannerType.valueOf(payload.bannerType());
                            GachaManager.INSTANCE.addOrUpdateBanner(
                                     payload.id(), payload.title(), payload.subtitle(), type,
                                     payload.cost(), payload.discount(), payload.preview(),
                                     payload.backgroundImage(),
                                     player.getServer()
                            );
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Ling Gacha Admin] Banner '" + payload.title() + "' saved successfully!"));
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 4. Admin Delete Banner (Client -> Server)
        registrar.playToServer(
                AdminDeleteBannerPayload.TYPE,
                AdminDeleteBannerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            GachaManager.INSTANCE.deleteBanner(payload.id(), player.getServer());
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Ling Gacha Admin] Banner deleted."));
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 5. Admin Add Item to Banner (Client -> Server)
        registrar.playToServer(
                AdminAddGachaItemPayload.TYPE,
                AdminAddGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            GachaRarity rarity = GachaRarity.fromStars(payload.stars());
                            GachaItemEntry entry = new GachaItemEntry(
                                    payload.itemId(), payload.count(), rarity, payload.customName(), payload.weight(), payload.isRateUp(), payload.snbt()
                            );
                            GachaManager.INSTANCE.addItemToBanner(payload.bannerId(), entry, player.getServer());
                            sendItemPoolToPlayer(player, payload.bannerId());
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Ling Gacha Admin] Item added to pool."));
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 6. Admin Update Item in Banner (Client -> Server)
        registrar.playToServer(
                AdminUpdateGachaItemPayload.TYPE,
                AdminUpdateGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            GachaRarity rarity = GachaRarity.fromStars(payload.stars());
                            GachaItemEntry entry = new GachaItemEntry(
                                    payload.itemId(), payload.count(), rarity, payload.customName(), payload.weight(), payload.isRateUp(), payload.snbt()
                            );
                            GachaManager.INSTANCE.updateItemInBanner(payload.bannerId(), payload.itemIndex(), entry, player.getServer());
                            sendItemPoolToPlayer(player, payload.bannerId());
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Ling Gacha Admin] Item updated."));
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 7. Admin Remove Item from Banner (Client -> Server)
        registrar.playToServer(
                AdminRemoveGachaItemPayload.TYPE,
                AdminRemoveGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            GachaManager.INSTANCE.removeItemFromBanner(payload.bannerId(), payload.itemIndex(), player.getServer());
                            sendItemPoolToPlayer(player, payload.bannerId());
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Ling Gacha Admin] Item removed from pool."));
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 8. Admin Request Item Pool (Client -> Server)
        registrar.playToServer(
                AdminRequestItemPoolPayload.TYPE,
                AdminRequestItemPoolPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (canAdmin(player)) {
                            sendItemPoolToPlayer(player, payload.bannerId());
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Ling Gacha Admin] Permission denied! Requires OP level 2 or Creative mode."));
                        }
                    }
                })
        );

        // 9. Client Request History (Client -> Server)
        registrar.playToServer(
                RequestPullHistoryPayload.TYPE,
                RequestPullHistoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        sendHistoryToPlayer(player, payload.bannerFilter());
                    }
                })
        );

        // 10. Convene Result Response (Server -> Client)
        registrar.playToClient(
                ConveneResultPayload.TYPE,
                ConveneResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleConveneResult(payload);
                    }
                })
        );

        // 11. Sync Banner Data (Server -> Client)
        registrar.playToClient(
                SyncBannerDataPayload.TYPE,
                SyncBannerDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientGachaData.update(payload);
                        ClientHooks.openGachaScreen();
                    }
                })
        );

        // 12. Admin Sync Item Pool (Server -> Client)
        registrar.playToClient(
                AdminSyncItemPoolPayload.TYPE,
                AdminSyncItemPoolPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleSyncItemPool(payload);
                    }
                })
        );

        // 13. Sync Pull History (Server -> Client)
        registrar.playToClient(
                SyncPullHistoryPayload.TYPE,
                SyncPullHistoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleSyncHistory(payload);
                    }
                })
        );

        // 14. Client Request Mailbox (Client -> Server)
        registrar.playToServer(
                RequestMailboxPayload.TYPE,
                RequestMailboxPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        sendMailboxToPlayer(player);
                    }
                })
        );

        // 15. Server Sync Mailbox (Server -> Client)
        registrar.playToClient(
                SyncMailboxPayload.TYPE,
                SyncMailboxPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleSyncMailbox(payload);
                    }
                })
        );

        // 16. Client Claim Mailbox Item (Client -> Server)
        registrar.playToServer(
                ClaimMailboxItemPayload.TYPE,
                ClaimMailboxItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if ("ALL".equalsIgnoreCase(payload.mailId())) {
                            GachaManager.INSTANCE.claimAllMailboxItems(player);
                        } else {
                            GachaManager.INSTANCE.claimMailboxItem(player, payload.mailId());
                        }
                    }
                })
        );
    }

    public static void sendHistoryToPlayer(ServerPlayer player, String bannerFilter) {
        PlayerGachaData data = GachaManager.INSTANCE.getPlayerData(player.getUUID());
        List<PlayerGachaData.PullHistoryRecord> all = data.getHistory();
        List<SyncPullHistoryPayload.HistoryRecordData> filtered = new ArrayList<>();

        String filter = (bannerFilter == null || bannerFilter.isEmpty()) ? "ALL" : bannerFilter;

        for (PlayerGachaData.PullHistoryRecord r : all) {
            if (filter.equalsIgnoreCase("ALL") || r.getBannerId().equalsIgnoreCase(filter)) {
                filtered.add(new SyncPullHistoryPayload.HistoryRecordData(
                        r.getBannerId(), r.getItemId(), r.getItemName(), r.getStars(), r.getTimestamp(), r.getPityCount()
                ));
            }
        }

        int pity5 = filter.equalsIgnoreCase("ALL") ? 0 : data.getPity5Star(filter);
        int pity4 = filter.equalsIgnoreCase("ALL") ? 0 : data.getPity4Star(filter);
        boolean guaranteed = filter.equalsIgnoreCase("ALL") ? false : data.isGuaranteed(filter);
        int total = filter.equalsIgnoreCase("ALL") ? data.getTotalPulls() : data.getBannerPulls(filter);

        PacketDistributor.sendToPlayer(player, new SyncPullHistoryPayload(
                filter,
                filtered,
                pity5,
                pity4,
                guaranteed,
                total
        ));
    }

    public static void openGachaForPlayer(ServerPlayer player) {
        syncDataToPlayer(player);
    }

    public static void syncDataToPlayer(ServerPlayer player) {
        PlayerGachaData data = GachaManager.INSTANCE.getPlayerData(player.getUUID());
        List<GachaBanner> banners = GachaManager.INSTANCE.getBanners();

        List<SyncBannerDataPayload.ClientBannerInfo> clientBanners = new ArrayList<>();
        for (GachaBanner b : banners) {
            GachaItemEntry featured = b.getFeaturedRateUpItem();
            String previewItem = (featured != null) ? featured.getItemId() : b.getFeaturedPreviewItem();
            String featuredName = (featured != null && featured.getCustomName() != null) ? featured.getCustomName() : null;
            String featuredSnbt = (featured != null) ? featured.getSnbt() : null;
            int pity = data.getPity5Star(b.getId());

            clientBanners.add(new SyncBannerDataPayload.ClientBannerInfo(
                    b.getId(),
                    b.getTitle(),
                    b.getSubtitle(),
                    b.getBannerType().name(),
                    b.getCostPerPull(),
                    b.getTenPullDiscountPercent(),
                    previewItem,
                    featuredName,
                    featuredSnbt,
                    b.getBackgroundImage(),
                    b.getItems().size(),
                    pity
            ));
        }

        int charPity = data.getPity5Star("featured_character");
        int weapPity = data.getPity5Star("featured_weapon");
        int stdPity = data.getPity5Star("standard_convene");
        int corals = data.getAfterglowCorals();
        boolean isGuaranteed = data.isGuaranteed("featured_character");
        int mailboxCount = data.getMailboxCount();

        PacketDistributor.sendToPlayer(player, new SyncBannerDataPayload(clientBanners, charPity, weapPity, stdPity, corals, isGuaranteed, mailboxCount));
    }

    public static void sendMailboxToPlayer(ServerPlayer player) {
        PlayerGachaData data = GachaManager.INSTANCE.getPlayerData(player.getUUID());
        List<PlayerGachaData.MailboxItem> items = data.getMailbox();
        List<SyncMailboxPayload.MailboxItemData> list = new ArrayList<>();
        for (PlayerGachaData.MailboxItem item : items) {
            list.add(new SyncMailboxPayload.MailboxItemData(
                    item.getMailId(),
                    item.getBannerId(),
                    item.getItemId(),
                    item.getCount(),
                    item.getStars(),
                    item.getCustomName(),
                    item.getSnbt(),
                    item.getTimestamp()
            ));
        }
        PacketDistributor.sendToPlayer(player, new SyncMailboxPayload(list));
    }

    public static void sendItemPoolToPlayer(ServerPlayer player, String bannerId) {
        GachaManager.INSTANCE.getBanner(bannerId).ifPresent(b -> {
            List<AdminSyncItemPoolPayload.ItemEntryData> list = new ArrayList<>();
            List<GachaItemEntry> items = b.getItems();
            for (int i = 0; i < items.size(); i++) {
                GachaItemEntry item = items.get(i);
                list.add(new AdminSyncItemPoolPayload.ItemEntryData(
                        i, item.getItemId(), item.getCount(), item.getRarity().getStars(), item.getCustomName(), item.getWeight(), item.isRateUp(), item.getSnbt()
                ));
            }
            PacketDistributor.sendToPlayer(player, new AdminSyncItemPoolPayload(bannerId, list));
        });
    }

    public static boolean canAdmin(ServerPlayer player) {
        if (player == null) return false;
        if (player.hasPermissions(2) || player.isCreative()) return true;
        if (player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile())) return true;
        return false;
    }
}
