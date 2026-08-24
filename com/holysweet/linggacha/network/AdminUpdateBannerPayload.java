package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminUpdateBannerPayload(
        String id,
        String title,
        String subtitle,
        String bannerType,
        int cost,
        int discount,
        String preview,
        String backgroundImage
) implements CustomPacketPayload {

    public static final Type<AdminUpdateBannerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_update_banner"));

    public static final StreamCodec<FriendlyByteBuf, AdminUpdateBannerPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminUpdateBannerPayload::write,
            AdminUpdateBannerPayload::new
    );

    public AdminUpdateBannerPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readBoolean() ? buf.readUtf() : null
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(title);
        buf.writeUtf(subtitle);
        buf.writeUtf(bannerType);
        buf.writeVarInt(cost);
        buf.writeVarInt(discount);
        buf.writeUtf(preview);
        buf.writeBoolean(backgroundImage != null);
        if (backgroundImage != null) buf.writeUtf(backgroundImage);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
