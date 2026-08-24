package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestPullHistoryPayload(String bannerFilter) implements CustomPacketPayload {

    public static final Type<RequestPullHistoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "request_history"));

    public static final StreamCodec<FriendlyByteBuf, RequestPullHistoryPayload> STREAM_CODEC = StreamCodec.ofMember(
            RequestPullHistoryPayload::write,
            RequestPullHistoryPayload::new
    );

    public RequestPullHistoryPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerFilter);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
