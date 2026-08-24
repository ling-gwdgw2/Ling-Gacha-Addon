package com.holysweet.linggacha.gacha;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class PlayerGachaData {

    private final UUID playerUuid;

    // Independent Pity & Stats per Banner ID (Lowercase key)
    private final Map<String, Integer> bannerPity5 = new HashMap<>();
    private final Map<String, Integer> bannerPity4 = new HashMap<>();
    private final Map<String, Boolean> bannerGuaranteed = new HashMap<>();
    private final Map<String, Integer> bannerPulls = new HashMap<>();

    private int totalPulls = 0;
    private int afterglowCorals = 0;
    private final List<PullHistoryRecord> history = new ArrayList<>();
    private final List<MailboxItem> mailbox = new ArrayList<>();

    public static class MailboxItem {
        private final String mailId;
        private final String bannerId;
        private final String itemId;
        private final int count;
        private final int stars;
        private final String customName;
        private final String snbt;
        private final long timestamp;

        public MailboxItem(String mailId, String bannerId, String itemId, int count, int stars, String customName, String snbt, long timestamp) {
            this.mailId = mailId != null ? mailId : UUID.randomUUID().toString();
            this.bannerId = bannerId != null ? bannerId : "default";
            this.itemId = itemId != null ? itemId : "minecraft:air";
            this.count = Math.max(1, count);
            this.stars = stars;
            this.customName = customName;
            this.snbt = snbt;
            this.timestamp = timestamp;
        }

        public String getMailId() { return mailId; }
        public String getBannerId() { return bannerId; }
        public String getItemId() { return itemId; }
        public int getCount() { return count; }
        public int getStars() { return stars; }
        public String getCustomName() { return customName; }
        public String getSnbt() { return snbt; }
        public long getTimestamp() { return timestamp; }
    }

    public static class PullHistoryRecord {
        private final String bannerId;
        private final String itemId;
        private final String itemName;
        private final int stars;
        private final long timestamp;
        private final int pityCount;

        public PullHistoryRecord(String bannerId, String itemId, String itemName, int stars, long timestamp, int pityCount) {
            this.bannerId = bannerId;
            this.itemId = itemId;
            this.itemName = itemName;
            this.stars = stars;
            this.timestamp = timestamp;
            this.pityCount = pityCount;
        }

        public String getBannerId() { return bannerId; }
        public String getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getStars() { return stars; }
        public long getTimestamp() { return timestamp; }
        public int getPityCount() { return pityCount; }
    }

    public PlayerGachaData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public List<MailboxItem> getMailbox() {
        return Collections.unmodifiableList(mailbox);
    }

    public synchronized void addMailboxItem(MailboxItem item) {
        mailbox.add(0, item);
    }

    public synchronized boolean removeMailboxItem(String mailId) {
        if (mailId == null) return false;
        return mailbox.removeIf(m -> m.getMailId().equals(mailId));
    }

    public int getMailboxCount() {
        return mailbox.size();
    }

    public List<PullHistoryRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void addHistoryRecord(PullHistoryRecord record) {
        history.add(0, record); // Most recent first
        if (history.size() > 500) {
            history.remove(history.size() - 1);
        }
    }

    public int getPity5Star(String bannerId) {
        if (bannerId == null) return 0;
        return bannerPity5.getOrDefault(bannerId.toLowerCase(), 0);
    }

    public int getPity4Star(String bannerId) {
        if (bannerId == null) return 0;
        return bannerPity4.getOrDefault(bannerId.toLowerCase(), 0);
    }

    public boolean isGuaranteed(String bannerId) {
        if (bannerId == null) return false;
        return bannerGuaranteed.getOrDefault(bannerId.toLowerCase(), false);
    }

    public void setGuaranteed(String bannerId, boolean guaranteed) {
        if (bannerId != null) {
            bannerGuaranteed.put(bannerId.toLowerCase(), guaranteed);
        }
    }

    public int getBannerPulls(String bannerId) {
        if (bannerId == null) return 0;
        return bannerPulls.getOrDefault(bannerId.toLowerCase(), 0);
    }

    public void incrementPity(String bannerId) {
        totalPulls++;
        if (bannerId != null) {
            String key = bannerId.toLowerCase();
            bannerPity5.put(key, bannerPity5.getOrDefault(key, 0) + 1);
            bannerPity4.put(key, bannerPity4.getOrDefault(key, 0) + 1);
            bannerPulls.put(key, bannerPulls.getOrDefault(key, 0) + 1);
        }
    }

    public void resetPity5Star(String bannerId) {
        if (bannerId != null) {
            bannerPity5.put(bannerId.toLowerCase(), 0);
        }
    }

    public void resetPity4Star(String bannerId) {
        if (bannerId != null) {
            bannerPity4.put(bannerId.toLowerCase(), 0);
        }
    }

    // Fallback for legacy BannerType enum calls
    public int getPity5Star(GachaBanner.BannerType type) {
        return switch (type) {
            case FEATURED_RESONATOR -> getPity5Star("featured_character");
            case FEATURED_WEAPON -> getPity5Star("featured_weapon");
            case STANDARD -> getPity5Star("standard_convene");
        };
    }

    public int getPity4Star(GachaBanner.BannerType type) {
        return switch (type) {
            case FEATURED_RESONATOR -> getPity4Star("featured_character");
            case FEATURED_WEAPON -> getPity4Star("featured_weapon");
            case STANDARD -> getPity4Star("standard_convene");
        };
    }

    public boolean isCharacterGuaranteedFeatured() {
        return isGuaranteed("featured_character");
    }

    public void setCharacterGuaranteedFeatured(boolean guaranteed) {
        setGuaranteed("featured_character", guaranteed);
    }

    public int getTotalPulls() {
        return totalPulls;
    }

    public int getAfterglowCorals() {
        return afterglowCorals;
    }

    public void addCorals(int amount) {
        this.afterglowCorals = Math.max(0, this.afterglowCorals + amount);
    }

    public static PlayerGachaData load(UUID uuid) {
        PlayerGachaData data = new PlayerGachaData(uuid);
        File file = new File("config/ling_gacha/playerdata/" + uuid + ".json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Gson gson = new Gson();
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("total")) data.totalPulls = obj.get("total").getAsInt();
                    if (obj.has("corals")) data.afterglowCorals = obj.get("corals").getAsInt();

                    // Load Banner-specific pity maps
                    if (obj.has("bannerPity5")) {
                        JsonObject p5 = obj.getAsJsonObject("bannerPity5");
                        for (String k : p5.keySet()) {
                            data.bannerPity5.put(k.toLowerCase(), p5.get(k).getAsInt());
                        }
                    }
                    if (obj.has("bannerPity4")) {
                        JsonObject p4 = obj.getAsJsonObject("bannerPity4");
                        for (String k : p4.keySet()) {
                            data.bannerPity4.put(k.toLowerCase(), p4.get(k).getAsInt());
                        }
                    }
                    if (obj.has("bannerGuaranteed")) {
                        JsonObject bg = obj.getAsJsonObject("bannerGuaranteed");
                        for (String k : bg.keySet()) {
                            data.bannerGuaranteed.put(k.toLowerCase(), bg.get(k).getAsBoolean());
                        }
                    }
                    if (obj.has("bannerPulls")) {
                        JsonObject bp = obj.getAsJsonObject("bannerPulls");
                        for (String k : bp.keySet()) {
                            data.bannerPulls.put(k.toLowerCase(), bp.get(k).getAsInt());
                        }
                    }

                    // Legacy migration fallback
                    if (!data.bannerPity5.containsKey("featured_character") && obj.has("char5")) {
                        data.bannerPity5.put("featured_character", obj.get("char5").getAsInt());
                    }
                    if (!data.bannerPity5.containsKey("featured_weapon") && obj.has("weap5")) {
                        data.bannerPity5.put("featured_weapon", obj.get("weap5").getAsInt());
                    }
                    if (!data.bannerPity5.containsKey("standard_convene") && obj.has("std5")) {
                        data.bannerPity5.put("standard_convene", obj.get("std5").getAsInt());
                    }
                    if (!data.bannerGuaranteed.containsKey("featured_character") && obj.has("charGuaranteed")) {
                        data.bannerGuaranteed.put("featured_character", obj.get("charGuaranteed").getAsBoolean());
                    }

                    if (obj.has("history")) {
                        JsonArray hArr = obj.getAsJsonArray("history");
                        for (int i = 0; i < hArr.size(); i++) {
                            JsonObject hObj = hArr.get(i).getAsJsonObject();
                            String bId = hObj.get("banner").getAsString();
                            String itemId = hObj.get("item").getAsString();
                            String name = hObj.has("name") ? hObj.get("name").getAsString() : itemId;
                            int stars = hObj.get("stars").getAsInt();
                            long time = hObj.get("time").getAsLong();
                            int pity = hObj.has("pity") ? hObj.get("pity").getAsInt() : 1;
                            data.history.add(new PullHistoryRecord(bId, itemId, name, stars, time, pity));
                        }
                    }

                    if (obj.has("mailbox")) {
                        JsonArray mArr = obj.getAsJsonArray("mailbox");
                        for (int i = 0; i < mArr.size(); i++) {
                            JsonObject mObj = mArr.get(i).getAsJsonObject();
                            String mailId = mObj.has("mailId") ? mObj.get("mailId").getAsString() : UUID.randomUUID().toString();
                            String bId = mObj.has("banner") ? mObj.get("banner").getAsString() : "default";
                            String itemId = mObj.get("item").getAsString();
                            int count = mObj.has("count") ? mObj.get("count").getAsInt() : 1;
                            int stars = mObj.get("stars").getAsInt();
                            String name = mObj.has("name") ? mObj.get("name").getAsString() : null;
                            String snbt = mObj.has("snbt") ? mObj.get("snbt").getAsString() : null;
                            long time = mObj.has("time") ? mObj.get("time").getAsLong() : System.currentTimeMillis();
                            data.mailbox.add(new MailboxItem(mailId, bId, itemId, count, stars, name, snbt, time));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return data;
    }

    public void save() {
        try {
            File dir = new File("config/ling_gacha/playerdata");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, playerUuid + ".json");
            JsonObject obj = new JsonObject();
            obj.addProperty("total", totalPulls);
            obj.addProperty("corals", afterglowCorals);

            // Save Banner Pity Maps
            JsonObject p5Obj = new JsonObject();
            for (Map.Entry<String, Integer> e : bannerPity5.entrySet()) {
                p5Obj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("bannerPity5", p5Obj);

            JsonObject p4Obj = new JsonObject();
            for (Map.Entry<String, Integer> e : bannerPity4.entrySet()) {
                p4Obj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("bannerPity4", p4Obj);

            JsonObject bgObj = new JsonObject();
            for (Map.Entry<String, Boolean> e : bannerGuaranteed.entrySet()) {
                bgObj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("bannerGuaranteed", bgObj);

            JsonObject bpObj = new JsonObject();
            for (Map.Entry<String, Integer> e : bannerPulls.entrySet()) {
                bpObj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("bannerPulls", bpObj);

            // Save History
            JsonArray hArr = new JsonArray();
            for (PullHistoryRecord h : history) {
                JsonObject hObj = new JsonObject();
                hObj.addProperty("banner", h.getBannerId());
                hObj.addProperty("item", h.getItemId());
                hObj.addProperty("name", h.getItemName());
                hObj.addProperty("stars", h.getStars());
                hObj.addProperty("time", h.getTimestamp());
                hObj.addProperty("pity", h.getPityCount());
                hArr.add(hObj);
            }
            obj.add("history", hArr);

            // Save Mailbox
            JsonArray mArr = new JsonArray();
            for (MailboxItem m : mailbox) {
                JsonObject mObj = new JsonObject();
                mObj.addProperty("mailId", m.getMailId());
                mObj.addProperty("banner", m.getBannerId());
                mObj.addProperty("item", m.getItemId());
                mObj.addProperty("count", m.getCount());
                mObj.addProperty("stars", m.getStars());
                if (m.getCustomName() != null) mObj.addProperty("name", m.getCustomName());
                if (m.getSnbt() != null) mObj.addProperty("snbt", m.getSnbt());
                mObj.addProperty("time", m.getTimestamp());
                mArr.add(mObj);
            }
            obj.add("mailbox", mArr);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(obj, writer);
            }
        } catch (Exception ignored) {}
    }
}
