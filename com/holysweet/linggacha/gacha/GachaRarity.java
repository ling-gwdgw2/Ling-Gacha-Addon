package com.holysweet.linggacha.gacha;

public enum GachaRarity {
    THREE_STAR(3, "3-Star", 0xFF4361EE, "§9"),
    FOUR_STAR(4, "4-Star", 0xFF9D4EDD, "§5"),
    FIVE_STAR(5, "5-Star", 0xFFFFB703, "§6§l");

    private final int stars;
    private final String displayName;
    private final int colorHex;
    private final String chatFormatting;

    GachaRarity(int stars, String displayName, int colorHex, String chatFormatting) {
        this.stars = stars;
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.chatFormatting = chatFormatting;
    }

    public int getStars() {
        return stars;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColorHex() {
        return colorHex;
    }

    public String getChatFormatting() {
        return chatFormatting;
    }

    public static GachaRarity fromStars(int stars) {
        if (stars >= 5) return FIVE_STAR;
        if (stars == 4) return FOUR_STAR;
        return THREE_STAR;
    }
}
