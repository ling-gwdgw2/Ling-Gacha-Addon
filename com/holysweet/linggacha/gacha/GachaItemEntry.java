package com.holysweet.linggacha.gacha;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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
    private final String snbt;

    public GachaItemEntry(String itemId, int count, GachaRarity rarity, String customName, int weight, boolean isRateUp) {
        this(itemId, count, rarity, customName, weight, isRateUp, null);
    }

    public GachaItemEntry(String itemId, int count, GachaRarity rarity, String customName, int weight, boolean isRateUp, String snbt) {
        this.itemId = itemId;
        this.count = Math.max(1, count);
        this.rarity = rarity;
        this.customName = customName;
        this.weight = Math.max(1, weight);
        this.isRateUp = isRateUp;
        this.snbt = snbt;
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

    public String getSnbt() {
        return snbt;
    }

    public ItemStack createItemStack() {
        return createItemStack(null);
    }

    public ItemStack createItemStack(HolderLookup.Provider registries) {
        if (snbt != null && !snbt.trim().isEmpty()) {
            try {
                CompoundTag tag = TagParser.parseTag(snbt);
                if (registries != null) {
                    ItemStack parsed = ItemStack.parseOptional(registries, tag);
                    if (!parsed.isEmpty()) {
                        if (customName != null && !customName.trim().isEmpty()) {
                            parsed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(customName));
                        }
                        return parsed;
                    }
                }
            } catch (Exception ignored) {}
        }
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

    public static GachaItemEntry fromItemStack(ItemStack stack, GachaRarity rarity, int weight, boolean isRateUp, String customNameOverride, HolderLookup.Provider registries) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int count = stack.getCount();
        String name = (customNameOverride != null && !customNameOverride.trim().isEmpty())
                ? customNameOverride
                : (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null);

        String snbt = null;
        if (registries != null) {
            try {
                var tag = stack.saveOptional(registries);
                snbt = tag.getAsString();
            } catch (Exception ignored) {}
        }

        return new GachaItemEntry(id, count, rarity, name, weight, isRateUp, snbt);
    }
}
