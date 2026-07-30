package org.loom.event;

/**
 * Emitted when a full row of the schematic is completed.
 * Triggers a progress save to disk.
 */
public record RowCompletedEvent(int row) implements LoomEvent {}
