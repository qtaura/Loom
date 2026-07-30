package org.loom.inventory;

import org.loom.util.Material;

/**
 * A request for a specific quantity of a material.
 */
public class MaterialRequest {

    private final Material material;
    private final int quantity;

    public MaterialRequest(Material material, int quantity) {
        this.material = material;
        this.quantity = quantity;
    }

    public Material getMaterial() {
        return material;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return "MaterialRequest{" + material + " x" + quantity + "}";
    }
}
