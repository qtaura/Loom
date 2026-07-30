package org.loom.event;

/**
 * Emitted when the printer is paused (e.g., for restock or user command).
 */
public record PrintPausedEvent(String jobId, int pausedAtX, int pausedAtY) implements LoomEvent {}
