package com.holysweet.linggacha.network;

import com.holysweet.linggacha.client.ClientGachaData;
import com.holysweet.linggacha.client.ClientHooks;
import com.holysweet.linggacha.gacha.GachaBanner;
import com.holysweet.linggacha.gacha.GachaItemEntry;
import com.holysweet.linggacha.gacha.GachaManager;
import com.holysweet.linggacha.gacha.PlayerGachaData;
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

        // 1. Pull Convene Request (Client -> Server)
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

        // 2. Convene Result Response (Server -> Client)
        registrar.playToClient(
                ConveneResultPayload.TYPE,
                ConveneResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        ClientHooks.handleConveneResult(payload);
                    }
                })
        );

        // 3. Sync Banner Data (Server -> Client)
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
    }

    public static void openGachaForPlayer(ServerPlayer player) {
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
}
