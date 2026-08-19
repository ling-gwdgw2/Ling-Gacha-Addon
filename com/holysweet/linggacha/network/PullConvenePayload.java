package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PullConvenePayload(String bannerId, int pullCount) implements CustomPacketPayload {

    public static final Type<PullConvenePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "pull_convene"));

    public static final StreamCodec<FriendlyByteBuf, PullConvenePayload> STREAM_CODEC = StreamCodec.ofMember(
            PullConvenePayload::write,
            PullConvenePayload::new
    );

    public PullConvenePayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerId);
        buf.writeVarInt(pullCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
