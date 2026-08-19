package com.holysweet.linggacha.gacha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GachaBanner {

    public enum BannerType {
        FEATURED_RESONATOR("Featured Character", true, false),
        FEATURED_WEAPON("Featured Weapon", false, true),
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
    private String title;
    private String subtitle;
    private BannerType bannerType;
    private int costPerPull;
    private int tenPullDiscountPercent;
    private String featuredPreviewItem;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public BannerType getBannerType() {
        return bannerType;
    }

    public void setBannerType(BannerType bannerType) {
        this.bannerType = bannerType;
    }

    public int getCostPerPull() {
        return costPerPull;
    }

    public void setCostPerPull(int costPerPull) {
        this.costPerPull = Math.max(1, costPerPull);
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

    public void setTenPullDiscountPercent(int tenPullDiscountPercent) {
        this.tenPullDiscountPercent = Math.max(0, Math.min(100, tenPullDiscountPercent));
    }

    public String getFeaturedPreviewItem() {
        return featuredPreviewItem;
    }

    public void setFeaturedPreviewItem(String featuredPreviewItem) {
        this.featuredPreviewItem = featuredPreviewItem;
    }

    public void addItem(GachaItemEntry entry) {
        items.add(entry);
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public void updateItem(int index, GachaItemEntry entry) {
        if (index >= 0 && index < items.size()) {
            items.set(index, entry);
        }
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

    public GachaItemEntry getFeaturedRateUpItem() {
        List<GachaItemEntry> rateUps = getRateUpItems(GachaRarity.FIVE_STAR);
        if (!rateUps.isEmpty()) return rateUps.get(0);
        List<GachaItemEntry> fives = getItemsByRarity(GachaRarity.FIVE_STAR);
        if (!fives.isEmpty()) return fives.get(0);
        List<GachaItemEntry> anyRateUps = items.stream().filter(GachaItemEntry::isRateUp).toList();
        if (!anyRateUps.isEmpty()) return anyRateUps.get(0);
        if (!items.isEmpty()) return items.get(0);
        return null;
    }
}
