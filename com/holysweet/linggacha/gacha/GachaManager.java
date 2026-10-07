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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    public void unloadPlayerData(UUID uuid) {
        PlayerGachaData data = playerDataCache.remove(uuid);
        if (data != null) {
            data.save();
        }
    }

    public void saveAllPlayerData() {
        for (PlayerGachaData data : playerDataCache.values()) {
            if (data != null) {
                data.save();
            }
        }
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

        if (banner.getItems().isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Convene] This banner currently has no items configured!"));
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

        try {
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
        } catch (Throwable t) {
            LOGGER.error("[Ling Gacha] Unexpected error during convene for player {}. Rolling back Primogems.", player.getName().getString(), t);
            // Rollback coins
            CoinsService.add(player.serverLevel(), player, totalCost);
            com.holysweet.questshop.network.Net.syncBalance(player);
            player.sendSystemMessage(Component.literal("§c[Convene] An unexpected error occurred. Your " + totalCost + " Primogems have been refunded!"));
            return Collections.emptyList();
        }
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
        if (!canFitInInventory(player.getInventory(), stack)) {
            player.sendSystemMessage(Component.literal("§c[Ling Gacha] Inventory full! Please free up space before claiming."));
            return;
        }

        int originalCount = stack.getCount();
        boolean added = player.getInventory().add(stack);
        if (added || stack.isEmpty()) {
            data.removeMailboxItem(mailId);
            data.save();
            GachaNet.sendMailboxToPlayer(player);
            GachaNet.syncDataToPlayer(player);
            String name = (mailItem.getCustomName() != null && !mailItem.getCustomName().isEmpty())
                    ? mailItem.getCustomName() : stack.getHoverName().getString();
            player.sendSystemMessage(Component.literal("§a[Ling Gacha] Claimed: " + name + " x" + originalCount + "!"));
        } else {
            // Partial addition fallback: update remaining count in mailbox to prevent duplication
            if (stack.getCount() < originalCount) {
                data.removeMailboxItem(mailId);
                data.addMailboxItem(new PlayerGachaData.MailboxItem(
                        mailId,
                        mailItem.getBannerId(),
                        mailItem.getItemId(),
                        stack.getCount(),
                        mailItem.getStars(),
                        mailItem.getCustomName(),
                        mailItem.getSnbt(),
                        mailItem.getTimestamp()
                ));
                data.save();
                GachaNet.sendMailboxToPlayer(player);
                GachaNet.syncDataToPlayer(player);
                player.sendSystemMessage(Component.literal("§e[Ling Gacha] Inventory partially full! Claimed x"
                        + (originalCount - stack.getCount()) + ", remaining x" + stack.getCount() + " in mailbox."));
            } else {
                player.sendSystemMessage(Component.literal("§c[Ling Gacha] Inventory full! Please free up space before claiming."));
            }
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
            if (!canFitInInventory(player.getInventory(), stack)) {
                failedCount++;
                continue;
            }

            int origCount = stack.getCount();
            boolean added = player.getInventory().add(stack);
            if (added || stack.isEmpty()) {
                data.removeMailboxItem(mailItem.getMailId());
                claimedCount++;
            } else {
                if (stack.getCount() < origCount) {
                    data.removeMailboxItem(mailItem.getMailId());
                    data.addMailboxItem(new PlayerGachaData.MailboxItem(
                            mailItem.getMailId(),
                            mailItem.getBannerId(),
                            mailItem.getItemId(),
                            stack.getCount(),
                            mailItem.getStars(),
                            mailItem.getCustomName(),
                            mailItem.getSnbt(),
                            mailItem.getTimestamp()
                    ));
                    claimedCount++;
                } else {
                    failedCount++;
                }
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

    public static boolean canFitInInventory(Inventory inv, ItemStack stack) {
        if (stack.isEmpty()) return true;
        int remaining = stack.getCount();
        int maxStack = Math.min(stack.getMaxStackSize(), inv.getMaxStackSize());

        for (int i = 0; i < 36; i++) {
            ItemStack slotItem = inv.getItem(i);
            if (slotItem.isEmpty()) {
                remaining -= maxStack;
            } else if (ItemStack.isSameItemSameComponents(slotItem, stack)) {
                int room = maxStack - slotItem.getCount();
                if (room > 0) {
                    remaining -= room;
                }
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createItemStackFromMail(ServerPlayer player, PlayerGachaData.MailboxItem mailItem) {
        ItemStack stack = ItemStack.EMPTY;
        int targetCount = Math.max(1, mailItem.getCount());

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
                    stack = new ItemStack(itemOpt.get(), targetCount);
                }
            } catch (Exception ignored) {}
        }
        if (stack.isEmpty()) {
            stack = new ItemStack(net.minecraft.world.item.Items.DIRT, targetCount);
        } else {
            stack.setCount(targetCount);
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
            return selectFourStar(banner);
        } else {
            return selectRandomItem(banner.getItemsByRarity(GachaRarity.THREE_STAR));
        }
    }

    private GachaItemEntry selectFourStar(GachaBanner banner) {
        List<GachaItemEntry> rateUp = banner.getRateUpItems(GachaRarity.FOUR_STAR);
        List<GachaItemEntry> standard = banner.getStandardItems(GachaRarity.FOUR_STAR);

        // If rate-up 4-stars exist on featured banners, give 50% chance for rate-up
        if (!rateUp.isEmpty() && (banner.getBannerType() == GachaBanner.BannerType.FEATURED_RESONATOR || banner.getBannerType() == GachaBanner.BannerType.FEATURED_WEAPON)) {
            if (standard.isEmpty() || ThreadLocalRandom.current().nextDouble() < 0.5) {
                return selectRandomItem(rateUp);
            } else {
                return selectRandomItem(standard);
            }
        }

        List<GachaItemEntry> allFourStars = banner.getItemsByRarity(GachaRarity.FOUR_STAR);
        if (!allFourStars.isEmpty()) {
            return selectRandomItem(allFourStars);
        }
        return selectRandomItem(banner.getItems());
    }

    private GachaItemEntry selectFiveStar(ServerPlayer player, GachaBanner banner, PlayerGachaData data) {
        GachaItemEntry result;
        String bannerId = banner.getId();

        if (banner.getBannerType() == GachaBanner.BannerType.FEATURED_RESONATOR) {
            List<GachaItemEntry> rateUp = banner.getRateUpItems(GachaRarity.FIVE_STAR);
            List<GachaItemEntry> standard = banner.getStandardItems(GachaRarity.FIVE_STAR);

            // If there are no standard 5-stars configured, the player cannot lose 50/50
            if (data.isGuaranteed(bannerId) || standard.isEmpty() || ThreadLocalRandom.current().nextDouble() < 0.5) {
                // Won 50/50 or was guaranteed or no standard pool exists
                result = selectRandomItem(!rateUp.isEmpty() ? rateUp : banner.getItemsByRarity(GachaRarity.FIVE_STAR));
                data.setGuaranteed(bannerId, false);
            } else {
                // Lost 50/50 to an actual standard item
                result = selectRandomItem(standard);
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

        // Server-wide broadcast for legitimate 5-star items!
        if (result != null && result.getRarity() == GachaRarity.FIVE_STAR && player.getServer() != null) {
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
            File tempFile = new File(dir, "banners.json.tmp");
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
            try (FileWriter writer = new FileWriter(tempFile)) {
                gson.toJson(array, writer);
            }
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception eAtomic) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("[Ling Gacha] Failed to save banners.json", e);
        }
    }
}
