package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminUpdateGachaItemPayload(
        String bannerId,
        int itemIndex,
        String itemId,
        int count,
        int stars,
        String customName,
        int weight,
        boolean isRateUp,
        String snbt
) implements CustomPacketPayload {

    public static final Type<AdminUpdateGachaItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_update_item"));

    public static final StreamCodec<FriendlyByteBuf, AdminUpdateGachaItemPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminUpdateGachaItemPayload::write,
            AdminUpdateGachaItemPayload::new
    );

    public AdminUpdateGachaItemPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
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
        buf.writeUtf(bannerId);
        buf.writeVarInt(itemIndex);
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
