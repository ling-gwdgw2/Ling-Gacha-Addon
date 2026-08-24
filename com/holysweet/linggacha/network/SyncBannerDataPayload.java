package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncBannerDataPayload(List<ClientBannerInfo> banners, int charPity5, int weapPity5, int stdPity5, int corals, boolean isGuaranteed, int mailboxCount) implements CustomPacketPayload {

    public static final Type<SyncBannerDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "sync_banners"));

    public static final StreamCodec<FriendlyByteBuf, SyncBannerDataPayload> STREAM_CODEC = StreamCodec.ofMember(
            SyncBannerDataPayload::write,
            SyncBannerDataPayload::new
    );

    public record ClientBannerInfo(
            String id,
            String title,
            String subtitle,
            String type,
            int cost,
            int discount,
            String previewItem,
            String featuredItemName,
            String featuredSnbt,
            String backgroundImage,
            int totalItems,
            int currentPity5
    ) {
        public static ClientBannerInfo read(FriendlyByteBuf buf) {
            return new ClientBannerInfo(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(title);
            buf.writeUtf(subtitle);
            buf.writeUtf(type);
            buf.writeVarInt(cost);
            buf.writeVarInt(discount);
            buf.writeUtf(previewItem);
            buf.writeBoolean(featuredItemName != null);
            if (featuredItemName != null) buf.writeUtf(featuredItemName);
            buf.writeBoolean(featuredSnbt != null);
            if (featuredSnbt != null) buf.writeUtf(featuredSnbt);
            buf.writeBoolean(backgroundImage != null);
            if (backgroundImage != null) buf.writeUtf(backgroundImage);
            buf.writeVarInt(totalItems);
            buf.writeVarInt(currentPity5);
        }
    }

    public SyncBannerDataPayload(FriendlyByteBuf buf) {
        this(
                readBannerList(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt()
        );
    }

    private static List<ClientBannerInfo> readBannerList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ClientBannerInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(ClientBannerInfo.read(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(banners.size());
        for (ClientBannerInfo b : banners) {
            b.write(buf);
        }
        buf.writeVarInt(charPity5);
        buf.writeVarInt(weapPity5);
        buf.writeVarInt(stdPity5);
        buf.writeVarInt(corals);
        buf.writeBoolean(isGuaranteed);
        buf.writeVarInt(mailboxCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
