package org.loom.inventory.restock;

import org.loom.inventory.MaterialRequest;

import java.util.List;

/**
 * Defines what materials to restock and where the storage is.
 */
public class RestockRequest {

    private final List<MaterialRequest> materials;
    private final int storageX;
    private final int storageY;
    private final int storageZ;

    public RestockRequest(List<MaterialRequest> materials, int storageX, int storageY, int storageZ) {
        this.materials = List.copyOf(materials);
        this.storageX = storageX;
        this.storageY = storageY;
        this.storageZ = storageZ;
    }

    public List<MaterialRequest> getMaterials() {
        return materials;
    }

    public int getStorageX() {
        return storageX;
    }

    public int getStorageY() {
        return storageY;
    }

    public int getStorageZ() {
        return storageZ;
    }

    @Override
    public String toString() {
        return "RestockRequest{materials=" + materials.size()
            + " items, storage=(" + storageX + "," + storageY + "," + storageZ + ")}";
    }
}
