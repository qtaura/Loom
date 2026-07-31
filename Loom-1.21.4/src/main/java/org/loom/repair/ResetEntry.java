package org.loom.repair;

/**
 * A position in the build area that needs to be cleared during a reset.
 */
public class ResetEntry {

    private final int worldX;
    private final int worldY;
    private final int worldZ;

    public ResetEntry(int worldX, int worldY, int worldZ) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
    }

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public int getWorldZ() { return worldZ; }

    @Override
    public String toString() {
        return "ResetEntry(" + worldX + "," + worldY + "," + worldZ + ")";
    }
}
