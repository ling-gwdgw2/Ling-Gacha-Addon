package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminDeleteBannerPayload(String id) implements CustomPacketPayload {

    public static final Type<AdminDeleteBannerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_delete_banner"));

    public static final StreamCodec<FriendlyByteBuf, AdminDeleteBannerPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminDeleteBannerPayload::write,
            AdminDeleteBannerPayload::new
    );

    public AdminDeleteBannerPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
