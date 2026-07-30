package org.loom.util;

/**
 * Represents a material (block or item) used in map art construction.
 *
 * <p>Maps carpet colors to their Minecraft item/block identifiers.
 * For carpet map art, the key materials are the 16 dye colors of carpet.
 */
public class Material {

    // Common carpet colors for map art
    public static final Material WHITE_CARPET = new Material("minecraft:white_carpet", "White Carpet", true);
    public static final Material ORANGE_CARPET = new Material("minecraft:orange_carpet", "Orange Carpet", true);
    public static final Material MAGENTA_CARPET = new Material("minecraft:magenta_carpet", "Magenta Carpet", true);
    public static final Material LIGHT_BLUE_CARPET = new Material("minecraft:light_blue_carpet", "Light Blue Carpet", true);
    public static final Material YELLOW_CARPET = new Material("minecraft:yellow_carpet", "Yellow Carpet", true);
    public static final Material LIME_CARPET = new Material("minecraft:lime_carpet", "Lime Carpet", true);
    public static final Material PINK_CARPET = new Material("minecraft:pink_carpet", "Pink Carpet", true);
    public static final Material GRAY_CARPET = new Material("minecraft:gray_carpet", "Gray Carpet", true);
    public static final Material LIGHT_GRAY_CARPET = new Material("minecraft:light_gray_carpet", "Light Gray Carpet", true);
    public static final Material CYAN_CARPET = new Material("minecraft:cyan_carpet", "Cyan Carpet", true);
    public static final Material PURPLE_CARPET = new Material("minecraft:purple_carpet", "Purple Carpet", true);
    public static final Material BLUE_CARPET = new Material("minecraft:blue_carpet", "Blue Carpet", true);
    public static final Material BROWN_CARPET = new Material("minecraft:brown_carpet", "Brown Carpet", true);
    public static final Material GREEN_CARPET = new Material("minecraft:green_carpet", "Green Carpet", true);
    public static final Material RED_CARPET = new Material("minecraft:red_carpet", "Red Carpet", true);
    public static final Material BLACK_CARPET = new Material("minecraft:black_carpet", "Black Carpet", true);

    private final String identifier;
    private final String displayName;
    private final boolean isCarpet;

    public Material(String identifier, String displayName, boolean isCarpet) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.isCarpet = isCarpet;
    }

    public String getIdentifier() { return identifier; }
    public String getDisplayName() { return displayName; }
    public boolean isCarpet() { return isCarpet; }

    /**
     * Looks up a material by its Minecraft identifier string.
     *
     * @param identifier the Minecraft block/item ID (e.g. "minecraft:red_carpet")
     * @return the matching Material, or null if unknown
     */
    public static Material fromIdentifier(String identifier) {
        // TODO: Maintain a registry map for lookup
        // TODO: Support all 16 carpet colors + base blocks
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Material material)) return false;
        return identifier.equals(material.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return displayName + " (" + identifier + ")";
    }
}
