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
                            GachaBanner banner = GachaManager.INSTANCE.getBanner(payload.bannerId()).orElse(null);
                            int pity5 = banner != null ? data.getPity5Star(banner.getBannerType()) : 0;

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
                    if (context.player() instanceof ServerPlayer player && (player.hasPermissions(2) || player.isCreative())) {
                        GachaBanner.BannerType type = GachaBanner.BannerType.valueOf(payload.bannerType());
                        GachaManager.INSTANCE.addOrUpdateBanner(
                                payload.id(), payload.title(), payload.subtitle(), type,
                                payload.cost(), payload.discount(), payload.preview(),
                                player.getServer()
                        );
                    }
                })
        );

        // 4. Admin Delete Banner (Client -> Server)
        registrar.playToServer(
                AdminDeleteBannerPayload.TYPE,
                AdminDeleteBannerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && (player.hasPermissions(2) || player.isCreative())) {
                        GachaManager.INSTANCE.deleteBanner(payload.id(), player.getServer());
                    }
                })
        );

        // 5. Admin Add Item to Banner (Client -> Server)
        registrar.playToServer(
                AdminAddGachaItemPayload.TYPE,
                AdminAddGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && (player.hasPermissions(2) || player.isCreative())) {
                        GachaRarity rarity = GachaRarity.fromStars(payload.stars());
                        GachaItemEntry entry = new GachaItemEntry(
                                payload.itemId(), payload.count(), rarity, payload.customName(), payload.weight(), payload.isRateUp(), payload.snbt()
                        );
                        GachaManager.INSTANCE.addItemToBanner(payload.bannerId(), entry, player.getServer());
                        sendItemPoolToPlayer(player, payload.bannerId());
                    }
                })
        );

        // 6. Admin Update Item in Banner (Client -> Server)
        registrar.playToServer(
                AdminUpdateGachaItemPayload.TYPE,
                AdminUpdateGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && (player.hasPermissions(2) || player.isCreative())) {
                        GachaRarity rarity = GachaRarity.fromStars(payload.stars());
                        GachaItemEntry entry = new GachaItemEntry(
                                payload.itemId(), payload.count(), rarity, payload.customName(), payload.weight(), payload.isRateUp(), payload.snbt()
                        );
                        GachaManager.INSTANCE.updateItemInBanner(payload.bannerId(), payload.itemIndex(), entry, player.getServer());
                        sendItemPoolToPlayer(player, payload.bannerId());
                    }
                })
        );

        // 7. Admin Remove Item from Banner (Client -> Server)
        registrar.playToServer(
                AdminRemoveGachaItemPayload.TYPE,
                AdminRemoveGachaItemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && (player.hasPermissions(2) || player.isCreative())) {
                        GachaManager.INSTANCE.removeItemFromBanner(payload.bannerId(), payload.itemIndex(), player.getServer());
                        sendItemPoolToPlayer(player, payload.bannerId());
                    }
                })
        );

        // 8. Admin Request Item Pool (Client -> Server)
        registrar.playToServer(
                AdminRequestItemPoolPayload.TYPE,
                AdminRequestItemPoolPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        sendItemPoolToPlayer(player, payload.bannerId());
                    }
                })
        );

        // 9. Convene Result Response (Server -> Client)
        registrar.playToClient(
                ConveneResultPayload.TYPE,
                ConveneResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleConveneResult(payload);
                    }
                })
        );

        // 10. Sync Banner Data (Server -> Client)
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

        // 11. Admin Sync Item Pool (Server -> Client)
        registrar.playToClient(
                AdminSyncItemPoolPayload.TYPE,
                AdminSyncItemPoolPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleSyncItemPool(payload);
                    }
                })
        );
    }

    public static void openGachaForPlayer(ServerPlayer player) {
        syncDataToPlayer(player);
    }

    public static void syncDataToPlayer(ServerPlayer player) {
        PlayerGachaData data = GachaManager.INSTANCE.getPlayerData(player.getUUID());
        List<GachaBanner> banners = GachaManager.INSTANCE.getBanners();

        List<SyncBannerDataPayload.ClientBannerInfo> clientBanners = new ArrayList<>();
        for (GachaBanner b : banners) {
            clientBanners.add(new SyncBannerDataPayload.ClientBannerInfo(
                    b.getId(),
                    b.getTitle(),
                    b.getSubtitle(),
                    b.getBannerType().name(),
                    b.getCostPerPull(),
                    b.getTenPullDiscountPercent(),
                    b.getFeaturedPreviewItem(),
                    b.getItems().size()
            ));
        }

        int charPity = data.getPity5Star(GachaBanner.BannerType.FEATURED_RESONATOR);
        int weapPity = data.getPity5Star(GachaBanner.BannerType.FEATURED_WEAPON);
        int stdPity = data.getPity5Star(GachaBanner.BannerType.STANDARD);
        int corals = data.getAfterglowCorals();
        boolean isGuaranteed = data.isCharacterGuaranteedFeatured();

        PacketDistributor.sendToPlayer(player, new SyncBannerDataPayload(clientBanners, charPity, weapPity, stdPity, corals, isGuaranteed));
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
}
