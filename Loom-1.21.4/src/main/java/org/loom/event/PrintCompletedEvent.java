package org.loom.event;

/**
 * Emitted when a job completes successfully.
 */
public record PrintCompletedEvent(String jobId, int totalBlocksPlaced, double elapsedMinutes) implements LoomEvent {}
