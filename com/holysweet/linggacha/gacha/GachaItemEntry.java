package com.holysweet.linggacha.gacha;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public class GachaItemEntry {

    private final String itemId;
    private final int count;
    private final GachaRarity rarity;
    private final String customName;
    private final int weight;
    private final boolean isRateUp;

    public GachaItemEntry(String itemId, int count, GachaRarity rarity, String customName, int weight, boolean isRateUp) {
        this.itemId = itemId;
        this.count = Math.max(1, count);
        this.rarity = rarity;
        this.customName = customName;
        this.weight = Math.max(1, weight);
        this.isRateUp = isRateUp;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public GachaRarity getRarity() {
        return rarity;
    }

    public String getCustomName() {
        return customName;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isRateUp() {
        return isRateUp;
    }

    public ItemStack createItemStack() {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
            if (itemOpt.isPresent()) {
                ItemStack stack = new ItemStack(itemOpt.get(), count);
                if (customName != null && !customName.trim().isEmpty()) {
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(customName));
                }
                return stack;
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.DIRT, 1);
    }
}
