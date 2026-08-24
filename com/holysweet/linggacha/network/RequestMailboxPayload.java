package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestMailboxPayload() implements CustomPacketPayload {

    public static final Type<RequestMailboxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "request_mailbox"));

    public static final StreamCodec<FriendlyByteBuf, RequestMailboxPayload> STREAM_CODEC = StreamCodec.ofMember(
            RequestMailboxPayload::write,
            RequestMailboxPayload::new
    );

    public RequestMailboxPayload(FriendlyByteBuf buf) {
        this();
    }

    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
