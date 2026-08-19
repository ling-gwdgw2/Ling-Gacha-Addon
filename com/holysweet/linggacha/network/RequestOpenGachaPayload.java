package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestOpenGachaPayload() implements CustomPacketPayload {

    public static final Type<RequestOpenGachaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "request_open"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenGachaPayload> STREAM_CODEC = StreamCodec.ofMember(
            RequestOpenGachaPayload::write,
            RequestOpenGachaPayload::new
    );

    public RequestOpenGachaPayload(FriendlyByteBuf buf) {
        this();
    }

    public void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
