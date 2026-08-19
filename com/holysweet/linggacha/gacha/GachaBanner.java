package com.holysweet.linggacha.gacha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GachaBanner {

    public enum BannerType {
        FEATURED_RESONATOR("Featured Character", true, false),
        FEATURED_WEAPON("Featured Weapon", false, true),
        NOVICE("Novice Convene", false, false),
        STANDARD("Standard Convene", false, false);

        private final String displayName;
        private final boolean has5050;
        private final boolean isWeaponGuaranteed;

        BannerType(String displayName, boolean has5050, boolean isWeaponGuaranteed) {
            this.displayName = displayName;
            this.has5050 = has5050;
            this.isWeaponGuaranteed = isWeaponGuaranteed;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean has5050() {
            return has5050;
        }

        public boolean isWeaponGuaranteed() {
            return isWeaponGuaranteed;
        }
    }

    private final String id;
    private final String title;
    private final String subtitle;
    private final BannerType bannerType;
    private final int costPerPull;
    private final int tenPullDiscountPercent;
    private final String featuredPreviewItem;
    private final List<GachaItemEntry> items = new ArrayList<>();

    public GachaBanner(String id, String title, String subtitle, BannerType bannerType, int costPerPull, int tenPullDiscountPercent, String featuredPreviewItem) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.bannerType = bannerType;
        this.costPerPull = Math.max(1, costPerPull);
        this.tenPullDiscountPercent = Math.max(0, Math.min(100, tenPullDiscountPercent));
        this.featuredPreviewItem = featuredPreviewItem;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public BannerType getBannerType() {
        return bannerType;
    }

    public int getCostPerPull() {
        return costPerPull;
    }

    public int getTenPullCost() {
        if (tenPullDiscountPercent > 0) {
            int normal = costPerPull * 10;
            int discount = (normal * tenPullDiscountPercent) / 100;
            return Math.max(1, normal - discount);
        }
        return costPerPull * 10;
    }

    public int getTenPullDiscountPercent() {
        return tenPullDiscountPercent;
    }

    public String getFeaturedPreviewItem() {
        return featuredPreviewItem;
    }

    public void addItem(GachaItemEntry entry) {
        items.add(entry);
    }

    public List<GachaItemEntry> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<GachaItemEntry> getItemsByRarity(GachaRarity rarity) {
        return items.stream()
                .filter(i -> i.getRarity() == rarity)
                .collect(Collectors.toList());
    }

    public List<GachaItemEntry> getRateUpItems(GachaRarity rarity) {
        return items.stream()
                .filter(i -> i.getRarity() == rarity && i.isRateUp())
                .collect(Collectors.toList());
    }

    public List<GachaItemEntry> getStandardItems(GachaRarity rarity) {
        return items.stream()
                .filter(i -> i.getRarity() == rarity && !i.isRateUp())
                .collect(Collectors.toList());
    }
}
