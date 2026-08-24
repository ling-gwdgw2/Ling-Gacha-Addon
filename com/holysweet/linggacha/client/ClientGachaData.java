package com.holysweet.linggacha.client;

import com.holysweet.linggacha.network.SyncBannerDataPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientGachaData {

    private static final List<SyncBannerDataPayload.ClientBannerInfo> banners = new ArrayList<>();
    private static int charPity5 = 0;
    private static int weapPity5 = 0;
    private static int stdPity5 = 0;
    private static int corals = 0;
    private static boolean isGuaranteed = false;
    private static int lastSelectedBannerIndex = 0;
    private static int mailboxCount = 0;

    public static synchronized void update(SyncBannerDataPayload payload) {
        banners.clear();
        banners.addAll(payload.banners());
        charPity5 = payload.charPity5();
        weapPity5 = payload.weapPity5();
        stdPity5 = payload.stdPity5();
        corals = payload.corals();
        isGuaranteed = payload.isGuaranteed();
        mailboxCount = payload.mailboxCount();
        if (!banners.isEmpty()) {
            lastSelectedBannerIndex = Math.max(0, Math.min(banners.size() - 1, lastSelectedBannerIndex));
        }
    }

    public static int getMailboxCount() {
        return mailboxCount;
    }

    public static void setMailboxCount(int count) {
        mailboxCount = Math.max(0, count);
    }

    public static int getLastSelectedBannerIndex() {
        return lastSelectedBannerIndex;
    }

    public static void setLastSelectedBannerIndex(int index) {
        lastSelectedBannerIndex = Math.max(0, index);
    }

    public static List<SyncBannerDataPayload.ClientBannerInfo> getBanners() {
        return Collections.unmodifiableList(banners);
    }

    public static int getCharPity5() {
        return charPity5;
    }

    public static int getWeapPity5() {
        return weapPity5;
    }

    public static int getStdPity5() {
        return stdPity5;
    }

    public static int getCorals() {
        return corals;
    }

    public static boolean isGuaranteed() {
        return isGuaranteed;
    }

    public static int getPityForType(String type) {
        return switch (type) {
            case "FEATURED_RESONATOR" -> charPity5;
            case "FEATURED_WEAPON" -> weapPity5;
            default -> stdPity5;
        };
    }
}
