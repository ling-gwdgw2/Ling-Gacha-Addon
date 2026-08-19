package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record AdminSyncItemPoolPayload(
        String bannerId,
        List<ItemEntryData> items
) implements CustomPacketPayload {

    public record ItemEntryData(
            int index,
            String itemId,
            int count,
            int stars,
            String customName,
            int weight,
            boolean isRateUp,
            String snbt
    ) {
        public static ItemEntryData read(FriendlyByteBuf buf) {
            return new ItemEntryData(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean() ? buf.readUtf() : null
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeUtf(itemId);
            buf.writeVarInt(count);
            buf.writeVarInt(stars);
            buf.writeBoolean(customName != null);
            if (customName != null) buf.writeUtf(customName);
            buf.writeVarInt(weight);
            buf.writeBoolean(isRateUp);
            buf.writeBoolean(snbt != null);
            if (snbt != null) buf.writeUtf(snbt);
        }
    }

    public static final Type<AdminSyncItemPoolPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_sync_items"));

    public static final StreamCodec<FriendlyByteBuf, AdminSyncItemPoolPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminSyncItemPoolPayload::write,
            AdminSyncItemPoolPayload::new
    );

    public AdminSyncItemPoolPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readList(ItemEntryData::read)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerId);
        buf.writeCollection(items, (b, item) -> item.write(b));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
