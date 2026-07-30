package org.loom.event;

/**
 * Emitted when a single block is successfully placed.
 */
public record BlockPlacedEvent(int worldX, int worldY, int worldZ, String material) implements LoomEvent {}
