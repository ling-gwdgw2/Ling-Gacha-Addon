package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncPullHistoryPayload(
        String bannerFilter,
        List<HistoryRecordData> records,
        int current5StarPity,
        int current4StarPity,
        boolean isGuaranteed,
        int totalPulls
) implements CustomPacketPayload {

    public static final Type<SyncPullHistoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "sync_history"));

    public static final StreamCodec<FriendlyByteBuf, SyncPullHistoryPayload> STREAM_CODEC = StreamCodec.ofMember(
            SyncPullHistoryPayload::write,
            SyncPullHistoryPayload::new
    );

    public record HistoryRecordData(
            String bannerId,
            String itemId,
            String itemName,
            int stars,
            long timestamp,
            int pityCount
    ) {
        public static HistoryRecordData read(FriendlyByteBuf buf) {
            return new HistoryRecordData(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readVarInt()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(bannerId);
            buf.writeUtf(itemId);
            buf.writeUtf(itemName);
            buf.writeVarInt(stars);
            buf.writeLong(timestamp);
            buf.writeVarInt(pityCount);
        }
    }

    public SyncPullHistoryPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                readRecordList(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt()
        );
    }

    private static List<HistoryRecordData> readRecordList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<HistoryRecordData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(HistoryRecordData.read(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerFilter);
        buf.writeVarInt(records.size());
        for (HistoryRecordData r : records) {
            r.write(buf);
        }
        buf.writeVarInt(current5StarPity);
        buf.writeVarInt(current4StarPity);
        buf.writeBoolean(isGuaranteed);
        buf.writeVarInt(totalPulls);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
