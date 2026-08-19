package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminRemoveGachaItemPayload(
        String bannerId,
        int itemIndex
) implements CustomPacketPayload {

    public static final Type<AdminRemoveGachaItemPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "admin_remove_item"));

    public static final StreamCodec<FriendlyByteBuf, AdminRemoveGachaItemPayload> STREAM_CODEC = StreamCodec.ofMember(
            AdminRemoveGachaItemPayload::write,
            AdminRemoveGachaItemPayload::new
    );

    public AdminRemoveGachaItemPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerId);
        buf.writeVarInt(itemIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
