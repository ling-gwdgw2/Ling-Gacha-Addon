package com.holysweet.linggacha.gacha;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.holysweet.linggacha.network.GachaNet;
import com.holysweet.questshop.service.CoinsService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GachaManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final GachaManager INSTANCE = new GachaManager();

    private final List<GachaBanner> banners = new ArrayList<>();
    private final Map<UUID, PlayerGachaData> playerDataCache = new ConcurrentHashMap<>();

    private GachaManager() {}

    public List<GachaBanner> getBanners() {
        return Collections.unmodifiableList(banners);
    }

    public Optional<GachaBanner> getBanner(String id) {
        return banners.stream().filter(b -> b.getId().equalsIgnoreCase(id)).findFirst();
    }

    public PlayerGachaData getPlayerData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, PlayerGachaData::load);
    }

    public synchronized void addOrUpdateBanner(String id, String title, String subtitle, GachaBanner.BannerType type, int cost, int discount, String preview, String background, MinecraftServer server) {
        Optional<GachaBanner> existing = getBanner(id);
        if (existing.isPresent()) {
            GachaBanner b = existing.get();
            b.setTitle(title);
            b.setSubtitle(subtitle);
            b.setBannerType(type);
            b.setCostPerPull(cost);
            b.setTenPullDiscountPercent(discount);
            b.setFeaturedPreviewItem(preview);
            b.setBackgroundImage(background);
        } else {
            GachaBanner newBanner = new GachaBanner(id, title, subtitle, type, cost, discount, preview, background);
            banners.add(newBanner);
        }
        saveBanners();
        if (server != null) {
            syncAllOnlinePlayers(server);
        }
    }

    public synchronized void deleteBanner(String id, MinecraftServer server) {
        if (banners.size() <= 1) {
            return; // Keep at least 1 banner
        }
        banners.removeIf(b -> b.getId().equalsIgnoreCase(id));
        if (banners.isEmpty()) {
            createDefaultBanners();
        }
        saveBanners();
        if (server != null) {
            syncAllOnlinePlayers(server);
        }
    }

    public synchronized void addItemToBanner(String bannerId, GachaItemEntry entry, MinecraftServer server) {
        getBanner(bannerId).ifPresent(b -> {
            b.addItem(entry);
            saveBanners();
            if (server != null) {
                syncAllOnlinePlayers(server);
            }
        });
    }

    public synchronized void updateItemInBanner(String bannerId, int index, GachaItemEntry entry, MinecraftServer server) {
        getBanner(bannerId).ifPresent(b -> {
            b.updateItem(index, entry);
            saveBanners();
            if (server != null) {
                syncAllOnlinePlayers(server);
            }
        });
    }

    public synchronized void removeItemFromBanner(String bannerId, int index, MinecraftServer server) {
        getBanner(bannerId).ifPresent(b -> {
            b.removeItem(index);
            saveBanners();
            if (server != null) {
                syncAllOnlinePlayers(server);
            }
        });
    }

    public void syncAllOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GachaNet.syncDataToPlayer(player);
        }
    }

    public synchronized List<GachaItemEntry> performConvene(ServerPlayer player, String bannerId, int pullCount) {
        GachaBanner banner = getBanner(bannerId).orElse(null);
        if (banner == null || (pullCount != 1 && pullCount != 10)) {
            return Collections.emptyList();
        }

        int totalCost = (pullCount == 10) ? banner.getTenPullCost() : banner.getCostPerPull();
        int currentBalance = CoinsService.get(player.serverLevel(), player);

        if (currentBalance < totalCost) {
            player.sendSystemMessage(Component.literal("§c[Convene] Not enough Primogems! Need " + totalCost + " G."));
            return Collections.emptyList();
        }

        // Deduct Primogems
        CoinsService.add(player.serverLevel(), player, -totalCost);
        com.holysweet.questshop.network.Net.syncBalance(player);
        PlayerGachaData data = getPlayerData(player.getUUID());
        List<GachaItemEntry> results = new ArrayList<>();

        for (int i = 0; i < pullCount; i++) {
            int pityBefore = data.getPity5Star(bannerId);
            GachaItemEntry prize = rollSingle(player, banner, data);
            results.add(prize);

            // Record pull history
            String itemName = (prize.getCustomName() != null && !prize.getCustomName().isEmpty()) ? prize.getCustomName() : prize.getItemId();
            data.addHistoryRecord(new PlayerGachaData.PullHistoryRecord(
                    bannerId,
                    prize.getItemId(),
                    itemName,
                    prize.getRarity().getStars(),
                    System.currentTimeMillis(),
                    pityBefore + 1
            ));

            // Add item to persistent Mailbox (Safe Storage)
            data.addMailboxItem(new PlayerGachaData.MailboxItem(
                    UUID.randomUUID().toString(),
                    bannerId,
                    prize.getItemId(),
                    prize.getCount(),
                    prize.getRarity().getStars(),
                    prize.getCustomName(),
                    prize.getSnbt(),
                    System.currentTimeMillis()
            ));

            // Award Corals
            if (prize.getRarity() == GachaRarity.FIVE_STAR) {
                data.addCorals(15);
            } else if (prize.getRarity() == GachaRarity.FOUR_STAR) {
                data.addCorals(3);
            } else {
                data.addCorals(1);
            }
        }

        data.save();
        GachaNet.syncDataToPlayer(player);
        return results;
    }

    public synchronized void claimMailboxItem(ServerPlayer player, String mailId) {
        if (player == null || mailId == null) return;
        PlayerGachaData data = getPlayerData(player.getUUID());
        Optional<PlayerGachaData.MailboxItem> itemOpt = data.getMailbox().stream()
                .filter(m -> m.getMailId().equals(mailId))
                .findFirst();

        if (itemOpt.isEmpty()) return;
        PlayerGachaData.MailboxItem mailItem = itemOpt.get();

        ItemStack stack = createItemStackFromMail(player, mailItem);
        if (player.getInventory().add(stack)) {
            data.removeMailboxItem(mailId);
            data.save();
            GachaNet.sendMailboxToPlayer(player);
            GachaNet.syncDataToPlayer(player);
            String name = (mailItem.getCustomName() != null) ? mailItem.getCustomName() : stack.getHoverName().getString();
            player.sendSystemMessage(Component.literal("§a[Ling Gacha] Claimed: " + name + " x" + mailItem.getCount() + "!"));
        } else {
            player.sendSystemMessage(Component.literal("§c[Ling Gacha] Inventory full! Please free up space before claiming."));
        }
    }

    public synchronized void claimAllMailboxItems(ServerPlayer player) {
        if (player == null) return;
        PlayerGachaData data = getPlayerData(player.getUUID());
        List<PlayerGachaData.MailboxItem> items = new ArrayList<>(data.getMailbox());
        if (items.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e[Ling Gacha] Mailbox is empty."));
            return;
        }

        int claimedCount = 0;
        int failedCount = 0;

        for (PlayerGachaData.MailboxItem mailItem : items) {
            ItemStack stack = createItemStackFromMail(player, mailItem);
            if (player.getInventory().add(stack)) {
                data.removeMailboxItem(mailItem.getMailId());
                claimedCount++;
            } else {
                failedCount++;
            }
        }

        data.save();
        GachaNet.sendMailboxToPlayer(player);
        GachaNet.syncDataToPlayer(player);

        if (claimedCount > 0) {
            player.sendSystemMessage(Component.literal("§a[Ling Gacha] Successfully claimed " + claimedCount + " item(s)!"));
        }
        if (failedCount > 0) {
            player.sendSystemMessage(Component.literal("§c[Ling Gacha] Inventory full! " + failedCount + " item(s) remain safely in your Mailbox."));
        }
    }

    private ItemStack createItemStackFromMail(ServerPlayer player, PlayerGachaData.MailboxItem mailItem) {
        ItemStack stack = ItemStack.EMPTY;
        if (mailItem.getSnbt() != null && !mailItem.getSnbt().isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(mailItem.getSnbt());
                stack = ItemStack.parseOptional(player.registryAccess(), tag);
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(mailItem.getItemId());
                var itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (itemOpt.isPresent()) {
                    stack = new ItemStack(itemOpt.get(), mailItem.getCount());
                }
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            stack = new ItemStack(net.minecraft.world.item.Items.DIRT, mailItem.getCount());
        }
        if (mailItem.getCustomName() != null && !mailItem.getCustomName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(mailItem.getCustomName()));
        }
        return stack;
    }

    private GachaItemEntry rollSingle(ServerPlayer player, GachaBanner banner, PlayerGachaData data) {
        String bannerId = banner.getId();
        data.incrementPity(bannerId);
        int current5StarPity = data.getPity5Star(bannerId);
        int current4StarPity = data.getPity4Star(bannerId);

        // 1. Calculate 5-Star Probability with Soft & Hard Pity (Hard Pity at 80)
        double fiveStarChance = 0.008; // 0.8% base
        if (current5StarPity >= 80) {
            fiveStarChance = 1.0; // Hard Pity 80
        } else if (current5StarPity >= 65) {
            fiveStarChance = 0.008 + (current5StarPity - 64) * 0.06; // Soft Pity ramp up
        }

        // 2. Calculate 4-Star Probability (Hard Pity at 10)
        double fourStarChance = 0.06; // 6.0% base
        if (current4StarPity >= 10) {
            fourStarChance = 1.0; // Hard Pity 10
        }

        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < fiveStarChance) {
            data.resetPity5Star(bannerId);
            return selectFiveStar(player, banner, data);
        } else if (roll < (fiveStarChance + fourStarChance)) {
            data.resetPity4Star(bannerId);
            return selectRandomItem(banner.getItemsByRarity(GachaRarity.FOUR_STAR));
        } else {
            return selectRandomItem(banner.getItemsByRarity(GachaRarity.THREE_STAR));
        }
    }

    private GachaItemEntry selectFiveStar(ServerPlayer player, GachaBanner banner, PlayerGachaData data) {
        GachaItemEntry result;
        String bannerId = banner.getId();

        if (banner.getBannerType() == GachaBanner.BannerType.FEATURED_RESONATOR) {
            List<GachaItemEntry> rateUp = banner.getRateUpItems(GachaRarity.FIVE_STAR);
            List<GachaItemEntry> standard = banner.getStandardItems(GachaRarity.FIVE_STAR);

            if (data.isGuaranteed(bannerId) || ThreadLocalRandom.current().nextDouble() < 0.5) {
                // Won 50/50 or was guaranteed
                result = selectRandomItem(!rateUp.isEmpty() ? rateUp : banner.getItemsByRarity(GachaRarity.FIVE_STAR));
                data.setGuaranteed(bannerId, false);
            } else {
                // Lost 50/50
                result = selectRandomItem(!standard.isEmpty() ? standard : banner.getItemsByRarity(GachaRarity.FIVE_STAR));
                data.setGuaranteed(bannerId, true);
                player.sendSystemMessage(Component.literal("§e[Convene] 50/50 lost! Your next 5★ is 100% guaranteed featured!"));
            }
        } else if (banner.getBannerType() == GachaBanner.BannerType.FEATURED_WEAPON) {
            // 100% Rate-Up Guarantee
            List<GachaItemEntry> rateUp = banner.getRateUpItems(GachaRarity.FIVE_STAR);
            result = selectRandomItem(!rateUp.isEmpty() ? rateUp : banner.getItemsByRarity(GachaRarity.FIVE_STAR));
        } else {
            result = selectRandomItem(banner.getItemsByRarity(GachaRarity.FIVE_STAR));
        }

        // Server-wide broadcast for 5-star items!
        if (result != null && player.getServer() != null) {
            String msg = "§6§l★ GACHA ★ §f" + player.getName().getString() + " pulled 5★ §e" + (result.getCustomName() != null ? result.getCustomName() : result.getItemId()) + "§f!";
            player.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
        }

        return result;
    }

    private GachaItemEntry selectRandomItem(List<GachaItemEntry> pool) {
        if (pool == null || pool.isEmpty()) {
            return new GachaItemEntry("minecraft:iron_ingot", 1, GachaRarity.THREE_STAR, "Iron Ingot", 1, false);
        }
        int totalWeight = pool.stream().mapToInt(GachaItemEntry::getWeight).sum();
        int randomWeight = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
        int current = 0;
        for (GachaItemEntry entry : pool) {
            current += entry.getWeight();
            if (randomWeight < current) {
                return entry;
            }
        }
        return pool.get(0);
    }

    public void loadBanners() {
        File file = new File("config/ling_gacha/banners.json");
        if (!file.exists()) {
            createDefaultBanners();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonArray array = gson.fromJson(reader, JsonArray.class);
            if (array == null || array.isEmpty()) {
                createDefaultBanners();
                return;
            }

            banners.clear();
            for (int i = 0; i < array.size(); i++) {
                JsonObject obj = array.get(i).getAsJsonObject();
                String id = obj.get("id").getAsString();
                String title = obj.get("title").getAsString();
                String subtitle = obj.has("subtitle") ? obj.get("subtitle").getAsString() : "";
                GachaBanner.BannerType type = GachaBanner.BannerType.valueOf(obj.get("type").getAsString());
                int cost = obj.get("cost").getAsInt();
                int discount = obj.has("discount") ? obj.get("discount").getAsInt() : 0;
                String preview = obj.has("preview") ? obj.get("preview").getAsString() : "minecraft:netherite_sword";
                String background = obj.has("background") ? obj.get("background").getAsString() : null;

                GachaBanner banner = new GachaBanner(id, title, subtitle, type, cost, discount, preview, background);

                if (obj.has("items")) {
                    JsonArray itemsArr = obj.getAsJsonArray("items");
                    for (int j = 0; j < itemsArr.size(); j++) {
                        JsonObject itemObj = itemsArr.get(j).getAsJsonObject();
                        String itemId = itemObj.get("item").getAsString();
                        int count = itemObj.has("count") ? itemObj.get("count").getAsInt() : 1;
                        int stars = itemObj.get("stars").getAsInt();
                        String name = itemObj.has("name") ? itemObj.get("name").getAsString() : null;
                        int weight = itemObj.has("weight") ? itemObj.get("weight").getAsInt() : 1;
                        boolean rateUp = itemObj.has("rateUp") && itemObj.get("rateUp").getAsBoolean();
                        String snbt = itemObj.has("snbt") ? itemObj.get("snbt").getAsString() : null;

                        banner.addItem(new GachaItemEntry(itemId, count, GachaRarity.fromStars(stars), name, weight, rateUp, snbt));
                    }
                }
                banners.add(banner);
            }
            LOGGER.info("[Ling Gacha] Loaded {} convene banners from config.", banners.size());
        } catch (Exception e) {
            LOGGER.error("[Ling Gacha] Failed to load banners.json, fallback to defaults", e);
            createDefaultBanners();
        }
    }

    public void createDefaultBanners() {
        banners.clear();

        // 1. Featured Resonator Banner (Changli / Mythic Blade)
        GachaBanner charBanner = new GachaBanner("featured_character", "Vermillion Flight", "Featured 5★ Resonator & Gear (50/50)", GachaBanner.BannerType.FEATURED_RESONATOR, 160, 0, "minecraft:netherite_sword", "ling_gacha:textures/gui/banners/featured_character.png");
        charBanner.addItem(new GachaItemEntry("minecraft:netherite_sword", 1, GachaRarity.FIVE_STAR, "Blazing Sunblade (5★ Rate-Up)", 10, true));
        charBanner.addItem(new GachaItemEntry("minecraft:elytra", 1, GachaRarity.FIVE_STAR, "Wings of Vermillion (5★ Standard)", 5, false));
        charBanner.addItem(new GachaItemEntry("minecraft:totem_of_undying", 2, GachaRarity.FIVE_STAR, "Resonator Rebirth Totem (5★ Standard)", 5, false));

        charBanner.addItem(new GachaItemEntry("minecraft:diamond_chestplate", 1, GachaRarity.FOUR_STAR, "Solar Armor (4★)", 20, true));
        charBanner.addItem(new GachaItemEntry("minecraft:diamond_sword", 1, GachaRarity.FOUR_STAR, "Radiant Edge (4★)", 20, true));
        charBanner.addItem(new GachaItemEntry("minecraft:enchanted_golden_apple", 2, GachaRarity.FOUR_STAR, "Tacetite Elixir (4★)", 15, false));

        charBanner.addItem(new GachaItemEntry("minecraft:iron_block", 4, GachaRarity.THREE_STAR, "Iron Supply Block", 50, false));
        charBanner.addItem(new GachaItemEntry("minecraft:gold_ingot", 16, GachaRarity.THREE_STAR, "Gold Supply Ingot", 50, false));
        charBanner.addItem(new GachaItemEntry("minecraft:experience_bottle", 32, GachaRarity.THREE_STAR, "Resonance Potion", 50, false));
        banners.add(charBanner);

        // 2. Featured Weapon Banner (100% Guaranteed 5-Star Weapon)
        GachaBanner weapBanner = new GachaBanner("featured_weapon", "Absolute Pulsation", "Featured 5★ Weapon (100% Guaranteed)", GachaBanner.BannerType.FEATURED_WEAPON, 160, 0, "minecraft:netherite_axe", "ling_gacha:textures/gui/banners/featured_weapon.png");
        weapBanner.addItem(new GachaItemEntry("minecraft:netherite_axe", 1, GachaRarity.FIVE_STAR, "Verdant Summit (5★ Guaranteed)", 10, true));
        weapBanner.addItem(new GachaItemEntry("minecraft:trident", 1, GachaRarity.FOUR_STAR, "Tidecaller Spear (4★)", 25, true));
        weapBanner.addItem(new GachaItemEntry("minecraft:bow", 1, GachaRarity.FOUR_STAR, "Whisperwind Bow (4★)", 25, true));
        weapBanner.addItem(new GachaItemEntry("minecraft:diamond", 8, GachaRarity.THREE_STAR, "Astrite Crystal", 50, false));
        weapBanner.addItem(new GachaItemEntry("minecraft:lapis_block", 8, GachaRarity.THREE_STAR, "Resonance Core", 50, false));
        banners.add(weapBanner);

        // 3. Standard Convene (Permanent Pool)
        GachaBanner stdBanner = new GachaBanner("standard_convene", "Tidal Cadence", "Standard Permanent Convene", GachaBanner.BannerType.STANDARD, 160, 0, "minecraft:nether_star", "ling_gacha:textures/gui/banners/standard_convene.png");
        stdBanner.addItem(new GachaItemEntry("minecraft:nether_star", 1, GachaRarity.FIVE_STAR, "Celestial Star Core (5★)", 10, false));
        stdBanner.addItem(new GachaItemEntry("minecraft:dragon_egg", 1, GachaRarity.FIVE_STAR, "Dragon Heart (5★)", 5, false));
        stdBanner.addItem(new GachaItemEntry("minecraft:shulker_box", 2, GachaRarity.FOUR_STAR, "Pocket Void Storage (4★)", 25, false));
        stdBanner.addItem(new GachaItemEntry("minecraft:emerald_block", 4, GachaRarity.THREE_STAR, "Standard Emerald Cache", 50, false));
        stdBanner.addItem(new GachaItemEntry("minecraft:redstone_block", 8, GachaRarity.THREE_STAR, "Energy Capacitor", 50, false));
        banners.add(stdBanner);

        saveBanners();
    }

    public void saveBanners() {
        try {
            File dir = new File("config/ling_gacha");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "banners.json");
            JsonArray array = new JsonArray();

            for (GachaBanner banner : banners) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", banner.getId());
                obj.addProperty("title", banner.getTitle());
                obj.addProperty("subtitle", banner.getSubtitle());
                obj.addProperty("type", banner.getBannerType().name());
                obj.addProperty("cost", banner.getCostPerPull());
                obj.addProperty("discount", banner.getTenPullDiscountPercent());
                obj.addProperty("preview", banner.getFeaturedPreviewItem());
                if (banner.getBackgroundImage() != null && !banner.getBackgroundImage().trim().isEmpty()) {
                    obj.addProperty("background", banner.getBackgroundImage().trim());
                }

                JsonArray itemsArr = new JsonArray();
                for (GachaItemEntry item : banner.getItems()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("item", item.getItemId());
                    itemObj.addProperty("count", item.getCount());
                    itemObj.addProperty("stars", item.getRarity().getStars());
                    if (item.getCustomName() != null) itemObj.addProperty("name", item.getCustomName());
                    itemObj.addProperty("weight", item.getWeight());
                    itemObj.addProperty("rateUp", item.isRateUp());
                    if (item.getSnbt() != null) itemObj.addProperty("snbt", item.getSnbt());
                    itemsArr.add(itemObj);
                }
                obj.add("items", itemsArr);
                array.add(obj);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(array, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[Ling Gacha] Failed to save banners.json", e);
        }
    }
}
