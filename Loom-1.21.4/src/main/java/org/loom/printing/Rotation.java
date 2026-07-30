package org.loom.printing;

/**
 * Immutable rotation value (yaw and pitch in degrees).
 */
public record Rotation(float yaw, float pitch) {

    @Override
    public String toString() {
        return String.format("Rotation{yaw=%.1f, pitch=%.1f}", yaw, pitch);
    }
}
