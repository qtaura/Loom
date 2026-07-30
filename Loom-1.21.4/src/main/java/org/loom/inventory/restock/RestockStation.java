package org.loom.inventory.restock;

/**
 * Configuration for a single restock station (chest or group of chests).
 */
public class RestockStation {

    private final int x;
    private final int y;
    private final int z;
    private final String name;

    public RestockStation(int x, int y, int z, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "RestockStation{" + name + " at (" + x + "," + y + "," + z + ")}";
    }
}
