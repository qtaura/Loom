package org.loom.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a material (block or item) used in map art construction.
 *
 * <p>Maps carpet colors to their Minecraft item/block identifiers.
 * For carpet map art, the key materials are the 16 dye colors of carpet.
 */
public class Material {

    private static final Map<String, Material> REGISTRY = new HashMap<>();

    // Common carpet colors for map art
    public static final Material WHITE_CARPET      = register("minecraft:white_carpet",      "White Carpet",      true);
    public static final Material ORANGE_CARPET     = register("minecraft:orange_carpet",     "Orange Carpet",     true);
    public static final Material MAGENTA_CARPET    = register("minecraft:magenta_carpet",    "Magenta Carpet",    true);
    public static final Material LIGHT_BLUE_CARPET = register("minecraft:light_blue_carpet", "Light Blue Carpet", true);
    public static final Material YELLOW_CARPET     = register("minecraft:yellow_carpet",     "Yellow Carpet",     true);
    public static final Material LIME_CARPET       = register("minecraft:lime_carpet",       "Lime Carpet",       true);
    public static final Material PINK_CARPET       = register("minecraft:pink_carpet",       "Pink Carpet",       true);
    public static final Material GRAY_CARPET       = register("minecraft:gray_carpet",       "Gray Carpet",       true);
    public static final Material LIGHT_GRAY_CARPET = register("minecraft:light_gray_carpet", "Light Gray Carpet", true);
    public static final Material CYAN_CARPET       = register("minecraft:cyan_carpet",       "Cyan Carpet",       true);
    public static final Material PURPLE_CARPET     = register("minecraft:purple_carpet",     "Purple Carpet",     true);
    public static final Material BLUE_CARPET       = register("minecraft:blue_carpet",       "Blue Carpet",       true);
    public static final Material BROWN_CARPET      = register("minecraft:brown_carpet",      "Brown Carpet",      true);
    public static final Material GREEN_CARPET      = register("minecraft:green_carpet",      "Green Carpet",      true);
    public static final Material RED_CARPET        = register("minecraft:red_carpet",        "Red Carpet",        true);
    public static final Material BLACK_CARPET      = register("minecraft:black_carpet",      "Black Carpet",      true);

    private final String identifier;
    private final String displayName;
    private final boolean isCarpet;

    public Material(String identifier, String displayName, boolean isCarpet) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.isCarpet = isCarpet;
    }

    private static Material register(String identifier, String displayName, boolean isCarpet) {
        Material m = new Material(identifier, displayName, isCarpet);
        REGISTRY.put(identifier, m);
        return m;
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
        return REGISTRY.get(identifier);
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
