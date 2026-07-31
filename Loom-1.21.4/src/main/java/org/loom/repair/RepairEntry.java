package org.loom.repair;

/**
 * A single position that needs repair — the wrong block is present
 * and the correct block from the schematic needs to be placed.
 */
public class RepairEntry {

    private final int worldX;
    private final int worldY;
    private final int worldZ;
    private final int schematicX;
    private final int schematicY;
    private final String expectedMaterial;

    public RepairEntry(int worldX, int worldY, int worldZ,
                        int schematicX, int schematicY,
                        String expectedMaterial) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.schematicX = schematicX;
        this.schematicY = schematicY;
        this.expectedMaterial = expectedMaterial;
    }

    public int getWorldX() { return worldX; }
    public int getWorldY() { return worldY; }
    public int getWorldZ() { return worldZ; }
    public int getSchematicX() { return schematicX; }
    public int getSchematicY() { return schematicY; }
    public String getExpectedMaterial() { return expectedMaterial; }

    @Override
    public String toString() {
        return "RepairEntry{world=(" + worldX + "," + worldY + "," + worldZ
            + ") schematic=(" + schematicX + "," + schematicY
            + ") expected=" + expectedMaterial + "}";
    }
}
