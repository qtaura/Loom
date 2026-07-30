package org.loom.event;

/**
 * Emitted when a restock workflow fails (e.g., chest is empty, unreachable).
 */
public record RestockFailedEvent(String reason) implements LoomEvent {}
