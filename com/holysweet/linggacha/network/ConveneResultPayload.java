package com.holysweet.linggacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ConveneResultPayload(String bannerId, List<PrizeData> prizes, int highestStars, int pity5Star, int corals) implements CustomPacketPayload {

    public static final Type<ConveneResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ling_gacha", "convene_result"));

    public static final StreamCodec<FriendlyByteBuf, ConveneResultPayload> STREAM_CODEC = StreamCodec.ofMember(
            ConveneResultPayload::write,
            ConveneResultPayload::new
    );

    public record PrizeData(String itemId, int count, int stars, String name) {
        public static PrizeData read(FriendlyByteBuf buf) {
            return new PrizeData(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(itemId);
            buf.writeVarInt(count);
            buf.writeVarInt(stars);
            buf.writeUtf(name != null ? name : "");
        }
    }

    public ConveneResultPayload(FriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                readPrizeList(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    private static List<PrizeData> readPrizeList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<PrizeData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(PrizeData.read(buf));
        }
        return list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(bannerId);
        buf.writeVarInt(prizes.size());
        for (PrizeData prize : prizes) {
            prize.write(buf);
        }
        buf.writeVarInt(highestStars);
        buf.writeVarInt(pity5Star);
        buf.writeVarInt(corals);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
