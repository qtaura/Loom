package org.loom.state;

/**
 * A single progress entry recording that a block was placed at a
 * schematic-relative position.
 *
 * <p>Coordinates are schematic-relative (0,0 = bottom-left of schematic).
 * The caller maps world coordinates to schematic coordinates using the
 * job's origin offset.
 */
public class ProgressEntry {

    private final int x;
    private final int y;
    private final String material;

    public ProgressEntry(int x, int y, String material) {
        this.x = x;
        this.y = y;
        this.material = material;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getMaterial() {
        return material;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgressEntry that)) return false;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "ProgressEntry{(" + x + "," + y + ")=" + material + "}";
    }
}
