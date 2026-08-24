package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimMailboxItemPayload(String mailId) implements CustomPacketPayload {

    public static final Type<ClaimMailboxItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "claim_mailbox"));

    public static final StreamCodec<FriendlyByteBuf, ClaimMailboxItemPayload> STREAM_CODEC = StreamCodec.ofMember(
            ClaimMailboxItemPayload::write,
            ClaimMailboxItemPayload::new
    );

    public ClaimMailboxItemPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(mailId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
