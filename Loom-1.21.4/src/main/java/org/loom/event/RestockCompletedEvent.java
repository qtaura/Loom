package org.loom.event;

/**
 * Emitted when a restock workflow completes successfully.
 */
public record RestockCompletedEvent(int totalItemsRestocked) implements LoomEvent {}
