package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminAddGachaItemPayload(
        String bannerId,
        String itemId,
        int count,
        int stars,
        String customName,
        int weight,
        boolean isRateUp,
        String snbt
) implements CustomPacketPayload {

    public static final Type<AdminAddGachaItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_add_item"));

    public static final StreamCodec<FriendlyByteBuf, AdminAddGachaItemPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminAddGachaItemPayload::write,
            AdminAddGachaItemPayload::new
    );

    public AdminAddGachaItemPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
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
