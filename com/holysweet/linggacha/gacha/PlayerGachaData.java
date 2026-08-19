package com.holysweet.linggacha.gacha;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public class PlayerGachaData {

    private final UUID playerUuid;
    private int characterPity5Star = 0;
    private int characterPity4Star = 0;
    private boolean characterGuaranteedFeatured = false;

    private int weaponPity5Star = 0;
    private int weaponPity4Star = 0;

    private int standardPity5Star = 0;
    private int standardPity4Star = 0;

    private int totalPulls = 0;
    private int afterglowCorals = 0;

    public PlayerGachaData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getPity5Star(GachaBanner.BannerType type) {
        return switch (type) {
            case FEATURED_RESONATOR -> characterPity5Star;
            case FEATURED_WEAPON -> weaponPity5Star;
            case STANDARD -> standardPity5Star;
        };
    }

    public int getPity4Star(GachaBanner.BannerType type) {
        return switch (type) {
            case FEATURED_RESONATOR -> characterPity4Star;
            case FEATURED_WEAPON -> weaponPity4Star;
            case STANDARD -> standardPity4Star;
        };
    }

    public boolean isCharacterGuaranteedFeatured() {
        return characterGuaranteedFeatured;
    }

    public void setCharacterGuaranteedFeatured(boolean guaranteed) {
        this.characterGuaranteedFeatured = guaranteed;
    }

    public void incrementPity(GachaBanner.BannerType type) {
        totalPulls++;
        switch (type) {
            case FEATURED_RESONATOR -> {
                characterPity5Star++;
                characterPity4Star++;
            }
            case FEATURED_WEAPON -> {
                weaponPity5Star++;
                weaponPity4Star++;
            }
            case STANDARD -> {
                standardPity5Star++;
                standardPity4Star++;
            }
        }
    }

    public void resetPity5Star(GachaBanner.BannerType type) {
        switch (type) {
            case FEATURED_RESONATOR -> characterPity5Star = 0;
            case FEATURED_WEAPON -> weaponPity5Star = 0;
            case STANDARD -> standardPity5Star = 0;
        }
    }

    public void resetPity4Star(GachaBanner.BannerType type) {
        switch (type) {
            case FEATURED_RESONATOR -> characterPity4Star = 0;
            case FEATURED_WEAPON -> weaponPity4Star = 0;
            case STANDARD -> standardPity4Star = 0;
        }
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
                    if (obj.has("char5")) data.characterPity5Star = obj.get("char5").getAsInt();
                    if (obj.has("char4")) data.characterPity4Star = obj.get("char4").getAsInt();
                    if (obj.has("charGuaranteed")) data.characterGuaranteedFeatured = obj.get("charGuaranteed").getAsBoolean();
                    if (obj.has("weap5")) data.weaponPity5Star = obj.get("weap5").getAsInt();
                    if (obj.has("weap4")) data.weaponPity4Star = obj.get("weap4").getAsInt();
                    if (obj.has("std5")) data.standardPity5Star = obj.get("std5").getAsInt();
                    if (obj.has("std4")) data.standardPity4Star = obj.get("std4").getAsInt();
                    if (obj.has("total")) data.totalPulls = obj.get("total").getAsInt();
                    if (obj.has("corals")) data.afterglowCorals = obj.get("corals").getAsInt();
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
            obj.addProperty("char5", characterPity5Star);
            obj.addProperty("char4", characterPity4Star);
            obj.addProperty("charGuaranteed", characterGuaranteedFeatured);
            obj.addProperty("weap5", weaponPity5Star);
            obj.addProperty("weap4", weaponPity4Star);
            obj.addProperty("std5", standardPity5Star);
            obj.addProperty("std4", standardPity4Star);
            obj.addProperty("total", totalPulls);
            obj.addProperty("corals", afterglowCorals);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(obj, writer);
            }
        } catch (Exception ignored) {}
    }
}
