package org.loom.event;

/**
 * Emitted when a job fails and cannot continue.
 */
public record PrintFailedEvent(String jobId, String reason) implements LoomEvent {}
