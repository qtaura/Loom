package org.loom.scanning;

/**
 * A discrepancy between the expected block (from schematic) and the actual block
 * (from world scan) at a specific world position.
 */
public class Discrepancy {

    private final int worldX;
    private final int worldY;
    private final int worldZ;
    private final String expected;
    private final String actual;

    public Discrepancy(int worldX, int worldY, int worldZ, String expected, String actual) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.expected = expected;
        this.actual = actual;
    }

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public int getWorldZ() { return worldZ; }
    public String getExpected() { return expected; }
    public String getActual() { return actual; }

    @Override
    public String toString() {
        return "Discrepancy{(" + worldX + "," + worldY + "," + worldZ + ") expected=" + expected + " actual=" + actual + "}";
    }
}
