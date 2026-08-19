package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminRequestItemPoolPayload(String bannerId) implements CustomPacketPayload {

    public static final Type<AdminRequestItemPoolPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_request_items"));

    public static final StreamCodec<FriendlyByteBuf, AdminRequestItemPoolPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminRequestItemPoolPayload::write,
            AdminRequestItemPoolPayload::new
    );

    public AdminRequestItemPoolPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
