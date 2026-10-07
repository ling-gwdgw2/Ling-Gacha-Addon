package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record SyncMailboxPayload(List<MailboxItemData> items) implements CustomPacketPayload {

    public static final Type<SyncMailboxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "sync_mailbox"));

    public static final StreamCodec<FriendlyByteBuf, SyncMailboxPayload> STREAM_CODEC = StreamCodec.ofMember(
            SyncMailboxPayload::write,
            SyncMailboxPayload::new
    );

    public record MailboxItemData(
            String mailId,
            String bannerId,
            String itemId,
            int count,
            int stars,
            String customName,
            String snbt,
            long timestamp
    ) {
        public static MailboxItemData read(FriendlyByteBuf buf) {
            return new MailboxItemData(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readBoolean() ? buf.readUtf() : null,
                    buf.readLong()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(mailId);
            buf.writeUtf(bannerId);
            buf.writeUtf(itemId);
            buf.writeVarInt(count);
            buf.writeVarInt(stars);
            buf.writeBoolean(customName != null);
            if (customName != null) buf.writeUtf(customName);
            buf.writeBoolean(snbt != null);
            if (snbt != null) buf.writeUtf(snbt);
            buf.writeLong(timestamp);
        }
    }

    public SyncMailboxPayload(FriendlyByteBuf buf) {
        this(readItemList(buf));
    }

    private static List<MailboxItemData> readItemList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MailboxItemData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(MailboxItemData.read(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(items.size());
        for (MailboxItemData item : items) {
            item.write(buf);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
