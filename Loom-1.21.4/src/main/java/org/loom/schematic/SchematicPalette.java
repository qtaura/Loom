package org.loom.schematic;

/**
 * An entry in a schematic's block palette: maps a palette index to a material name.
 */
public class SchematicPalette {

    private final int index;
    private final String material;
    private final boolean isCarpet;

    public SchematicPalette(int index, String material, boolean isCarpet) {
        this.index = index;
        this.material = material;
        this.isCarpet = isCarpet;
    }

    public int getIndex() { return index; }
    public String getMaterial() { return material; }
    public boolean isCarpet() { return isCarpet; }

    @Override
    public String toString() {
        return index + "=" + material + (isCarpet ? " [carpet]" : "");
    }
}
